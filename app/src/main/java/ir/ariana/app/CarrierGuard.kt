package ir.ariana.app

import android.content.Context
import android.telephony.TelephonyManager

/** Allows the tunnel only when the inserted SIM belongs to Irancell (MCC 432, MNC 35). */
object CarrierGuard {
    private const val IRANCELL = "43235"

    fun isIrancell(context: Context): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return tm.simOperator == IRANCELL
    }
}
