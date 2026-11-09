package io.github.alexeygrigorev.smsgate

import android.content.Context

/** Pairing + status, in app-private storage (not backed up: allowBackup=false). */
class Config(context: Context) {
    private val p = context.getSharedPreferences("smsgate", Context.MODE_PRIVATE)

    var gateUrl: String get() = p.getString("url", "") ?: ""; set(v) = p.edit().putString("url", v.trimEnd('/')).apply()
    var resource: String get() = p.getString("resource", "") ?: ""; set(v) = p.edit().putString("resource", v).apply()
    var token: String get() = p.getString("token", "") ?: ""; set(v) = p.edit().putString("token", v).apply()
    val paired get() = gateUrl.startsWith("https://") && resource.isNotBlank() && token.isNotBlank()

    var lastHeartbeatOkMs: Long get() = p.getLong("hb_ok", 0); set(v) = p.edit().putLong("hb_ok", v).apply()
    var lastPushMs: Long get() = p.getLong("push", 0); set(v) = p.edit().putLong("push", v).apply()
    var lastError: String get() = p.getString("err", "") ?: ""; set(v) = p.edit().putString("err", v).apply()
    var running: Boolean get() = p.getBoolean("running", false); set(v) = p.edit().putBoolean("running", v).apply()
    fun clearPairing() = p.edit().remove("url").remove("resource").remove("token").apply()
}
