package io.github.alexeygrigorev.smsgate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeExtractorTest {
    @Test fun plainCode() = assertEquals("4821", CodeExtractor.extract("Your code is 4821. Do not share."))
    @Test fun groupedCode() = assertEquals("123456", CodeExtractor.extract("Verification: 123 456"))
    @Test fun prefersNumberAfterKeyword() = assertEquals("987654", CodeExtractor.extract("Order 20261109 ready. Your code: 987654"))
    @Test fun noCode() = assertNull(CodeExtractor.extract("Hello, see you at 9"))
    @Test fun ignoresLongNumbers() = assertNull(CodeExtractor.extract("Ref 123456789012"))
}

class SenderTest {
    @Test fun nameCaseInsensitive() = assertTrue(Sender.matches("DHL", "dhl"))
    @Test fun numberIgnoresFormatting() = assertTrue(Sender.matches("+49 170 1234567", "491701234567"))
    @Test fun differentSender() = assertFalse(Sender.matches("Other", "DHL"))
    @Test fun blankNeverMatches() { assertFalse(Sender.matches(null, "DHL")); assertFalse(Sender.matches("DHL", "")) }
}

class CodeModelTest {
    @Test fun toStringHidesCode() = assertFalse(Code("DHL", "4821", 1L).toString().contains("4821"))
}
