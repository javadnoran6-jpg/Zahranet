package ir.ariana.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var spinner: Spinner
    private var connected = false

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) startTunnel()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
        }
        root.addView(TextView(this).apply { text = "Ariana"; textSize = 36f; gravity = Gravity.CENTER })
        root.addView(TextView(this).apply { text = getString(R.string.developer); gravity = Gravity.CENTER })

        spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                Mode.values().map { it.label }
            )
        }
        root.addView(spinner)

        status = TextView(this).apply { gravity = Gravity.CENTER; setPadding(0, 32, 0, 32) }
        root.addView(status)

        val button = Button(this).apply { text = "Connect" }
        button.setOnClickListener {
            if (connected) {
                startService(Intent(this@MainActivity, ArianaVpnService::class.java)
                    .setAction(ArianaVpnService.ACTION_STOP))
                connected = false
                button.text = "Connect"
                status.text = "Disconnected"
                return@setOnClickListener
            }
            when {
                !CarrierGuard.isIrancell(this@MainActivity) ->
                    status.text = "Ariana works only with an Irancell SIM."
                !CoreRunner.isAvailable(this@MainActivity) ->
                    status.text = "Engine missing for this device (arm64 only)."
                else -> {
                    val prep = VpnService.prepare(this@MainActivity)
                    if (prep != null) vpnPermission.launch(prep) else startTunnel()
                    connected = true
                    button.text = "Disconnect"
                    status.text = "Connecting..."
                }
            }
        }
        root.addView(button)
        setContentView(root)
        status.text = if (CarrierGuard.isIrancell(this)) "Irancell SIM detected" else "No Irancell SIM"
    }

    private fun startTunnel() {
        startService(Intent(this, ArianaVpnService::class.java)
            .putExtra(ArianaVpnService.EXTRA_MODE, spinner.selectedItemPosition))
        status.text = "Connected (proxy 127.0.0.1:${CoreRunner.HTTP_PORT})"
    }
}
