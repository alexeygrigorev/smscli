package io.github.alexeygrigorev.smsgate

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.concurrent.thread

/** Single screen: pairing, permission, start/stop, and a loud status line (heartbeat age, last error). */
class MainActivity : Activity() {
    private lateinit var cfg: Config
    private lateinit var status: TextView
    private lateinit var url: EditText
    private lateinit var resource: EditText
    private lateinit var token: EditText
    private val ui = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable { override fun run() { render(); ui.postDelayed(this, 5_000) } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        cfg = Config(this)
        handlePairLink(intent)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        status = TextView(this).apply { textSize = 16f }
        url = EditText(this).apply { hint = "Gate URL (https://…)"; setText(cfg.gateUrl) }
        resource = EditText(this).apply { hint = "Resource id"; setText(cfg.resource) }
        token = EditText(this).apply { hint = if (cfg.token.isBlank()) "Device token" else "Device token (saved, leave empty to keep)" }
        fun button(label: String, onClick: () -> Unit) = Button(this).apply { text = label; setOnClickListener { onClick() } }
        listOf(
            status, url, resource, token,
            button("Save pairing") { savePairing() },
            button("Grant SMS permission") { requestPermissions(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.POST_NOTIFICATIONS), 1) },
            button("Start") { start() },
            button("Stop") { HeartbeatService.stop(this); render() },
            button("Heartbeat now") { thread { Sync.tick(this); runOnUiThread { render() } } },
        ).forEach(root::addView)
        setContentView(root)
    }

    private fun handlePairLink(i: Intent?) {
        val d = i?.data ?: return
        if (d.scheme != "smsgate") return
        d.getQueryParameter("url")?.let { cfg.gateUrl = it }
        d.getQueryParameter("resource")?.let { cfg.resource = it }
        d.getQueryParameter("token")?.let { cfg.token = it }
    }

    private fun savePairing() {
        val u = url.text.toString().trim()
        if (!u.startsWith("https://")) { status.text = "ERROR: gate URL must be https://"; status.setTextColor(Color.RED); return }
        cfg.gateUrl = u; cfg.resource = resource.text.toString().trim()
        token.text.toString().trim().takeIf { it.isNotEmpty() }?.let { cfg.token = it }
        token.setText("")
        render()
    }

    private fun start() {
        if (!cfg.paired) { cfg.lastError = "not paired with a Resource Gate"; render(); return }
        if (!Sync.smsPermitted(this)) { cfg.lastError = "READ_SMS permission not granted"; render(); return }
        HeartbeatService.start(this); render()
    }

    override fun onResume() { super.onResume(); ui.post(refresh) }
    override fun onPause() { ui.removeCallbacks(refresh); super.onPause() }

    private fun render() {
        val now = System.currentTimeMillis()
        val hb = if (cfg.lastHeartbeatOkMs == 0L) "never" else DateUtils.getRelativeTimeSpanString(cfg.lastHeartbeatOkMs, now, DateUtils.SECOND_IN_MILLIS).toString()
        val stale = cfg.lastHeartbeatOkMs == 0L || now - cfg.lastHeartbeatOkMs > 180_000
        val problems = buildList {
            if (!cfg.paired) add("not paired")
            if (!Sync.smsPermitted(this@MainActivity)) add("READ_SMS permission missing")
            if (!cfg.running) add("service stopped")
            if (cfg.running && stale) add("heartbeat stale (>180s)")
            if (cfg.lastError.isNotBlank()) add(cfg.lastError)
        }
        status.text = (if (problems.isEmpty()) "OK" else "PROBLEM: " + problems.joinToString("; ")) +
            "\nheartbeat: $hb\nresource: ${cfg.resource.ifBlank { "-" }}"
        status.setTextColor(if (problems.isEmpty()) Color.rgb(0, 120, 0) else Color.RED)
    }
}
