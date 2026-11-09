package io.github.alexeygrigorev.smsgate

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** HTTPS client for the Resource Gate. Every call has hard timeouts; failures throw [GateException], never pass silently. */
class GateClient(private val baseUrl: String, private val resource: String, private val token: String) {
    fun heartbeat(appVersion: String, smsPermission: Boolean): GateState {
        val body = JSONObject().put("app_version", appVersion).put("sms_permission", smsPermission)
        val r = post("/v1/heartbeat/$resource", body)
        return GateState(r.optBoolean("grant_open"), r.optString("sender"), r.optInt("max_age_s", 120))
    }

    fun push(code: Code) {
        val body = JSONObject().put("sender", code.sender).put("code", code.code)
            .put("received_at", code.receivedAtMs / 1000)
        post("/v1/push/$resource", body)
    }

    private fun post(path: String, body: JSONObject): JSONObject {
        val conn = try {
            (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
            }
        } catch (e: Exception) {
            throw GateException("bad gate url: ${e.javaClass.simpleName}", false)
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val status = conn.responseCode
            val text = (if (status < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (status in 200..299) return if (text.isBlank()) JSONObject() else JSONObject(text)
            // Server messages are error text only; never echo request bodies.
            val detail = runCatching { JSONObject(text).optString("error") }.getOrDefault("").take(200)
            throw GateException("gate answered HTTP $status ${detail}".trim(), status >= 500)
        } catch (e: GateException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            throw GateException("gate timed out after ${READ_TIMEOUT_MS / 1000}s", true)
        } catch (e: java.io.IOException) {
            throw GateException("gate unreachable (${e.javaClass.simpleName}) - phone offline?", true)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 10_000
    }
}
