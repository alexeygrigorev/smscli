package io.github.alexeygrigorev.smsgate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import kotlin.concurrent.thread

/** Heartbeat every 60s so the gate (and the UI) can see the phone is alive; stale after 180s server-side. */
class HeartbeatService : Service() {
    @Volatile private var alive = false
    private var consecutiveFailures = 0

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Gate status", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Gate errors", NotificationManager.IMPORTANCE_HIGH))
        startForeground(ID, status("Starting…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        Config(this).running = true
        if (!alive) {
            alive = true
            thread(name = "smsgate-heartbeat") { loop(nm) }
        }
        return START_STICKY
    }

    private fun loop(nm: NotificationManager) {
        while (alive) {
            val err = Sync.tick(this)
            if (err == null) {
                consecutiveFailures = 0
                nm.cancel(ID_ALERT)
                nm.notify(ID, status("Connected, heartbeat ok"))
            } else {
                consecutiveFailures++
                nm.notify(ID, status("ERROR: $err"))
                // Loud on the phone after 2 misses; the gate raises its own server-side alert from the missing heartbeat.
                if (consecutiveFailures >= 2) nm.notify(ID_ALERT, alert(err))
            }
            try { Thread.sleep(INTERVAL_MS) } catch (_: InterruptedException) { return }
        }
    }

    override fun onDestroy() {
        alive = false
        Config(this).running = false
        super.onDestroy()
    }

    private fun status(text: String): Notification =
        Notification.Builder(this, CH).setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("SMS Gate").setContentText(text).setOngoing(true).build()

    private fun alert(text: String): Notification =
        Notification.Builder(this, CH_ALERT).setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("SMS Gate is NOT working").setContentText(text).build()

    companion object {
        const val INTERVAL_MS = 60_000L
        private const val CH = "status"; private const val CH_ALERT = "alert"
        private const val ID = 1; private const val ID_ALERT = 2

        fun start(ctx: Context) { ctx.startForegroundService(Intent(ctx, HeartbeatService::class.java)) }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, HeartbeatService::class.java)); Config(ctx).running = false }
    }
}
