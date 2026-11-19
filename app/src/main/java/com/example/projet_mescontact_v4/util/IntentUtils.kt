package com.example.projet_mescontact_v4.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.projet_mescontact_v4.R

/** Actions partagées par la liste et le détail. */
object IntentUtils {

    /** ACTION_DIAL : ouvre le composeur prérempli, sans permission (≠ ACTION_CALL). */
    fun dial(context: Context, phone: String?): Boolean {
        val number = phone?.trim().orEmpty()
        if (number.isEmpty()) {
            Toast.makeText(context, R.string.error_phone_empty, Toast.LENGTH_SHORT).show()
            return false
        }
        return try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.msg_no_dialer, Toast.LENGTH_SHORT).show()
            false
        }
    }
}
