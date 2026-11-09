package io.github.alexeygrigorev.smsgate

/** What may leave the phone: one sender, one code, one timestamp. Never the message body. */
data class Code(val sender: String, val code: String, val receivedAtMs: Long) {
    // Keep the code out of logs even if a Code is accidentally interpolated.
    override fun toString() = "Code(sender=$sender, code=***, receivedAtMs=$receivedAtMs)"
}

data class GateState(val grantOpen: Boolean, val sender: String, val maxAgeSeconds: Int)

class GateException(message: String, val transient: Boolean) : Exception(message)
