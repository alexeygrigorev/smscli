package io.github.alexeygrigorev.smsgate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Resume heartbeats after a reboot if the app was running and is still paired. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val cfg = Config(ctx)
        if (cfg.paired && cfg.running) {
            try { HeartbeatService.start(ctx) } catch (e: Exception) { cfg.lastError = "could not restart after boot: ${e.javaClass.simpleName}" }
        }
    }
}
