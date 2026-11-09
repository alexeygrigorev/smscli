package io.github.alexeygrigorev.smsgate

object Sender {
    /** Case-insensitive; phone numbers compare on digits only so "+49 170 1" == "491701". */
    fun matches(actual: String?, allowed: String): Boolean {
        if (actual.isNullOrBlank() || allowed.isBlank()) return false
        return norm(actual) == norm(allowed)
    }

    private fun norm(s: String): String {
        val t = s.trim()
        val digitsOnly = t.filter { it.isDigit() }
        return if (digitsOnly.length >= 6 && t.all { it.isDigit() || it in "+ -()" }) digitsOnly
        else t.lowercase()
    }
}
