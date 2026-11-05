package ir.zahranet.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import hev.htproxy.TProxyService
import ir.zahranet.app.AetherVpnService.State

class MainActivity : AppCompatActivity() {

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var power: ImageButton
    private lateinit var tvStatus: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvRx: TextView
    private lateinit var tvTx: TextView
    private lateinit var tvNote: TextView
    private lateinit var tvLog: TextView
    private lateinit var modes: RadioGroup

    private val modeFlags = mapOf(
        R.id.rbMasque to "--masque", R.id.rbGool to "--gool",
        R.id.rbPsiphon to "--psiphon", R.id.rbTor to "--tor"
    )

    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) launchService() else tvNote.text = "مجوز VPN داده نشد"
    }
    private val notifConsent = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val ticker = object : Runnable {
        override fun run() { render(); ui.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        power = findViewById(R.id.power)
        tvStatus = findViewById(R.id.tvStatus)
        tvTimer = findViewById(R.id.tvTimer)
        tvRx = findViewById(R.id.tvRx)
        tvTx = findViewById(R.id.tvTx)
        tvNote = findViewById(R.id.tvNote)
        tvLog = findViewById(R.id.tvLog)
        modes = findViewById(R.id.modes)
        power.setOnClickListener { onPower() }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifConsent.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() { super.onResume(); ui.post(ticker) }
    override fun onPause() { ui.removeCallbacks(ticker); super.onPause() }

    private fun onPower() {
        if (AetherVpnService.state != State.OFF) {
            startService(Intent(this, AetherVpnService::class.java).setAction(AetherVpnService.ACTION_STOP))
            return
        }
        if (!AetherVpnService.isIrancell(this)) { render(); return }
        val consent = VpnService.prepare(this)
        if (consent == null) launchService() else vpnConsent.launch(consent)
    }

    private fun launchService() {
        val flag = modeFlags[modes.checkedRadioButtonId] ?: "--masque"
        ContextCompat.startForegroundService(
            this, Intent(this, AetherVpnService::class.java).putExtra(AetherVpnService.EXTRA_MODE, flag)
        )
        ui.postDelayed({ render() }, 300)
    }

    private fun render() {
        val sim = AetherVpnService.isIrancell(this)
        val st = AetherVpnService.state
        val color = when {
            !sim -> 0xFFFF4D6D.toInt()
            st == State.ON -> 0xFF22D68A.toInt()
            st == State.CONNECTING -> 0xFFFFC857.toInt()
            else -> 0xFF7B2FFF.toInt()
        }
        power.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        power.isEnabled = sim
        tvStatus.text = when {
            !sim -> "فقط سیم‌کارت ایرانسل"
            st == State.ON -> "متصل است"
            st == State.CONNECTING -> "در حال اتصال…"
            else -> "قطع است"
        }
        tvStatus.setTextColor(if (st == State.ON) 0xFF22D68A.toInt() else if (st == State.CONNECTING) 0xFFFFC857.toInt() else 0xFFFF4D6D.toInt())
        for (i in 0 until modes.childCount) modes.getChildAt(i).isEnabled = st == State.OFF

        if (st == State.ON) {
            val s = ((SystemClock.elapsedRealtime() - AetherVpnService.since) / 1000).toInt()
            tvTimer.text = "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
            try {
                val x = TProxyService.TProxyGetStats()
                tvTx.text = fmt(x[1]); tvRx.text = fmt(x[3])
            } catch (_: Throwable) { }
        } else {
            tvTimer.text = if (sim) "برای اتصال کلیک کنید" else "این برنامه فقط روی ایرانسل کار می‌کند"
            tvTx.text = "—"; tvRx.text = "—"
        }
        tvNote.text = AetherVpnService.lastError
        tvLog.text = AetherVpnService.logTail.toList().takeLast(8).joinToString("\n")
    }

    private fun fmt(b: Long) =
        if (b < 1_048_576) "%.1f KB".format(b / 1024.0) else "%.1f MB".format(b / 1_048_576.0)
}
