package io.github.alexeygrigorev.smsgate

import android.content.Context
import android.net.Uri
import android.provider.Telephony

class SmsReader(private val context: Context) {
    /** Newest code from [sender] no older than [maxAgeSeconds], or null. Only the code leaves this method. */
    fun newestCode(sender: String, maxAgeSeconds: Int, nowMs: Long = System.currentTimeMillis()): Code? {
        val since = nowMs - maxAgeSeconds * 1000L
        val cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?", arrayOf(since.toString()),
            "${Telephony.Sms.DATE} DESC",
        ) ?: throw SecurityException("SMS provider returned no cursor (permission revoked?)")
        cursor.use {
            while (it.moveToNext()) {
                val address = it.getString(0)
                if (!Sender.matches(address, sender)) continue
                val code = CodeExtractor.extract(it.getString(1) ?: "") ?: continue
                return Code(sender, code, it.getLong(2))
            }
        }
        return null
    }
}
