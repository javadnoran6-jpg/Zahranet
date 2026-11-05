package ir.zahranet.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.Os
import android.telephony.TelephonyManager
import hev.htproxy.TProxyService
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Runs the real Aether core (libaether.so) as a child process, waits until it opens its local
 * SOCKS5 port (it does so only after its tunnel is verified), then routes the whole device
 * through that port using a TUN interface + hev-socks5-tunnel. No simulated state anywhere.
 */
class AetherVpnService : VpnService() {

    enum class State { OFF, CONNECTING, ON }

    companion object {
        const val ACTION_STOP = "ir.zahranet.app.STOP"
        const val EXTRA_MODE = "mode"
        const val SOCKS_PORT = 1819
        val ALLOWED_MODES = setOf("--masque", "--gool", "--psiphon", "--tor")
        private const val CHANNEL = "vpn"

        @Volatile var state = State.OFF
        @Volatile var since = 0L          // SystemClock.elapsedRealtime() at connect
        @Volatile var lastError = ""
        val logTail = ConcurrentLinkedDeque<String>()

        fun isIrancell(ctx: Context): Boolean {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            return tm.simOperator == "43235" // MCC 432 / MNC 35
        }
    }

    private val lock = Any()
    private var proc: Process? = null
    private var tun: ParcelFileDescriptor? = null
    private var tproxyStarted = false
    @Volatile private var gen = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { teardown(); return START_NOT_STICKY }
        if (state != State.OFF) return START_NOT_STICKY

        val mode = intent?.getStringExtra(EXTRA_MODE)
        if (mode == null || mode !in ALLOWED_MODES) { stopSelf(); return START_NOT_STICKY }

        goForeground()
        if (!isIrancell(this)) {
            lastError = "این برنامه فقط با سیم‌کارت ایرانسل کار می‌کند."
            teardown(); return START_NOT_STICKY
        }
        lastError = ""
        logTail.clear()
        state = State.CONNECTING
        val myGen = ++gen
        Thread({ connect(mode, myGen) }, "aether-connect").start()
        return START_NOT_STICKY
    }

    private fun connect(mode: String, myGen: Int) {
        try {
            val libDir = File(applicationInfo.nativeLibraryDir)
            val core = File(libDir, "libaether.so")
            if (!core.exists()) error("libaether.so در برنامه وجود ندارد")
            linkPluggableTransports(libDir)

            val p = ProcessBuilder(core.path, mode, "--stats")
                .directory(filesDir).redirectErrorStream(true).start()
            synchronized(lock) { if (gen != myGen) { p.destroyForcibly(); return }; proc = p }
            Thread({
                p.inputStream.bufferedReader().forEachLine {
                    logTail.add(it); while (logTail.size > 60) logTail.pollFirst()
                }
            }, "aether-log").apply { isDaemon = true }.start()

            // SOCKS5 opens only after the core has verified the tunnel.
            val deadline = SystemClock.elapsedRealtime() + 90_000
            var up = false
            while (SystemClock.elapsedRealtime() < deadline && gen == myGen && p.isAlive) {
                try {
                    Socket().use { it.connect(InetSocketAddress("127.0.0.1", SOCKS_PORT), 400) }
                    up = true; break
                } catch (_: Exception) { Thread.sleep(500) }
            }
            if (gen != myGen) return
            if (!up) error("هسته اتصال را برقرار نکرد (جزئیات در لاگ)")

            val pfd = Builder()
                .setSession("Ariana VPN")
                .setMtu(8500)
                .addAddress("198.18.0.1", 32)
                .addAddress("fc00::1", 128)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("198.18.0.2") // answered by hev mapdns, resolved through SOCKS5
                .addDisallowedApplication(packageName) // keep the core's own traffic outside the tunnel
                .establish() ?: error("سیستم ساخت VPN را رد کرد")
            synchronized(lock) {
                if (gen != myGen) { pfd.close(); return }
                tun = pfd
            }

            val cfg = File(filesDir, "tproxy.yml")
            cfg.writeText(
                """
                tunnel:
                  mtu: 8500
                  ipv4: 198.18.0.1
                  ipv6: 'fc00::1'
                socks5:
                  port: $SOCKS_PORT
                  address: 127.0.0.1
                  udp: 'udp'
                mapdns:
                  address: 198.18.0.2
                  port: 53
                  network: 100.64.0.0
                  netmask: 255.192.0.0
                  cache-size: 10000
                misc:
                  log-level: warn
                """.trimIndent()
            )
            TProxyService.TProxyStartService(cfg.path, pfd.fd)
            tproxyStarted = true
            since = SystemClock.elapsedRealtime()
            state = State.ON

            // If the core dies, the VPN must not stay "connected".
            Thread({ p.waitFor(); if (gen == myGen) { lastError = "هسته متوقف شد"; teardown() } }, "aether-watch")
                .apply { isDaemon = true }.start()
        } catch (t: Throwable) {
            if (gen == myGen) { lastError = t.message ?: t.javaClass.simpleName; teardown() }
        }
    }

    /** Aether's pt/ helpers ship as libpt_<name>.so (see scripts/fetch_native.sh); expose them as filesDir/pt/<name>. */
    private fun linkPluggableTransports(libDir: File) {
        val pt = File(filesDir, "pt")
        pt.deleteRecursively(); pt.mkdirs()
        libDir.listFiles { f -> f.name.startsWith("libpt_") && f.name.endsWith(".so") }?.forEach {
            val name = it.name.removePrefix("libpt_").removeSuffix(".so")
            try { Os.symlink(it.path, File(pt, name).path) } catch (_: Exception) {}
        }
    }

    private fun teardown() {
        synchronized(lock) {
            gen++
            if (tproxyStarted) { try { TProxyService.TProxyStopService() } catch (_: Throwable) {}; tproxyStarted = false }
            proc?.destroyForcibly(); proc = null
            try { tun?.close() } catch (_: Exception) {}; tun = null
            state = State.OFF
            since = 0
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() { teardown() }
    override fun onDestroy() { if (state != State.OFF) teardown(); super.onDestroy() }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "VPN", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Ariana VPN")
            .setContentText("VPN فعال است")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(1, n)
        }
    }
}
