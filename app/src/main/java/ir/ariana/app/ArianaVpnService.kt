package ir.ariana.app

import android.content.Intent
import android.net.ProxyInfo
import android.net.VpnService
import android.os.ParcelFileDescriptor

/**
 * Proxy-mode VPN: starts the Aether core, then registers a system-wide
 * HTTP proxy (127.0.0.1:1820) through VpnService. Apps that honour the
 * system proxy (browsers and most apps) go through Aether.
 * For full-tunnel capture of every packet, add a tun2socks library and
 * pipe the TUN fd to the SOCKS5 port 1819.
 */
class ArianaVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        if (!CarrierGuard.isIrancell(this)) {
            shutdown()
            return START_NOT_STICKY
        }
        val mode = Mode.values().getOrElse(intent?.getIntExtra(EXTRA_MODE, 0) ?: 0) { Mode.AUTO }
        try {
            CoreRunner.start(this, mode)
            tun = Builder()
                .setSession("Ariana")
                .addAddress("10.255.0.2", 32)
                .addRoute("10.255.0.0", 24)
                .setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", CoreRunner.HTTP_PORT))
                .setBlocking(false)
                .establish()
        } catch (e: Exception) {
            shutdown()
        }
        return START_STICKY
    }

    private fun shutdown() {
        CoreRunner.stop()
        tun?.close()
        tun = null
        stopSelf()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "ir.ariana.app.STOP"
        const val EXTRA_MODE = "mode"
    }
}
