package io.github.alexeygrigorev.smsgate

/** Pulls a one-time code out of a message body. The body itself never leaves the phone. */
object CodeExtractor {
    private val keyword = Regex("""(?i)(code|kod|код|pin|otp|passcode|verification|bestätigung)""")
    private val digits = Regex("""(?<![\d])(\d{4,8})(?![\d])""")
    private val grouped = Regex("""(?<![\d])(\d{3})[ -](\d{3})(?![\d])""")

    fun extract(body: String): String? {
        grouped.find(body)?.let { return it.groupValues[1] + it.groupValues[2] }
        val all = digits.findAll(body).toList()
        if (all.isEmpty()) return null
        // Prefer a number that follows a "code"-like keyword, else the first candidate.
        val kw = keyword.find(body)
        if (kw != null) {
            all.firstOrNull { it.range.first >= kw.range.first }?.let { return it.groupValues[1] }
        }
        return all.first().groupValues[1]
    }
}
