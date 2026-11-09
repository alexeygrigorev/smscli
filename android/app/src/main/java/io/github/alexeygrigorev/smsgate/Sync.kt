package io.github.alexeygrigorev.smsgate

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

/** One heartbeat + (if the gate says a grant is open) one push of the newest matching code. Records outcome in [Config]. */
object Sync {
    fun smsPermitted(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** @return null on success, or the loud error text (also persisted in Config.lastError). */
    fun tick(ctx: Context): String? {
        val cfg = Config(ctx)
        if (!cfg.paired) return fail(cfg, "not paired with a Resource Gate")
        val permitted = smsPermitted(ctx)
        return try {
            val client = GateClient(cfg.gateUrl, cfg.resource, cfg.token)
            val state = client.heartbeat(appVersion(ctx), permitted)
            cfg.lastHeartbeatOkMs = System.currentTimeMillis()
            if (!permitted) return fail(cfg, "READ_SMS permission lost - grant it in Settings")
            if (state.grantOpen) {
                val code = SmsReader(ctx).newestCode(state.sender, state.maxAgeSeconds)
                if (code != null) { client.push(code); cfg.lastPushMs = System.currentTimeMillis() }
            }
            cfg.lastError = ""
            null
        } catch (e: GateException) {
            fail(cfg, e.message ?: "gate error")
        } catch (e: SecurityException) {
            fail(cfg, "SMS access denied: ${e.message}")
        }
    }

    /** Push just-arrived code without waiting for the next heartbeat. */
    fun pushLive(ctx: Context, sender: String, body: String) {
        val cfg = Config(ctx)
        if (!cfg.paired) return
        try {
            val client = GateClient(cfg.gateUrl, cfg.resource, cfg.token)
            val state = client.heartbeat(appVersion(ctx), smsPermitted(ctx))
            cfg.lastHeartbeatOkMs = System.currentTimeMillis()
            if (!state.grantOpen || !Sender.matches(sender, state.sender)) return
            val code = CodeExtractor.extract(body) ?: return
            client.push(Code(state.sender, code, System.currentTimeMillis()))
            cfg.lastPushMs = System.currentTimeMillis()
            cfg.lastError = ""
        } catch (e: GateException) {
            cfg.lastError = e.message ?: "gate error"
        }
    }

    private fun fail(cfg: Config, msg: String): String { cfg.lastError = msg; return msg }

    private fun appVersion(ctx: Context) =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
}
