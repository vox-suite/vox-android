package `in`.voxagent.mobile.sms

import org.junit.Assert.*
import org.junit.Test

class OtpDetectorTest {
    @Test
    fun preservesFinancialContextWithoutOtp() {
        val body = "OTP is 654321 for transaction of INR 1,250 on credit card XX4321 at AMAZON"
        val result = sanitizeSmsBody(body)!!
        assertFalse(result.contains("654321"))
        assertTrue(result.contains("1,250"))
        assertTrue(result.contains("XX4321"))
        assertTrue(result.contains("AMAZON"))
    }

    @Test
    fun supportsCodeBeforeLabel() {
        val result = sanitizeSmsBody("654321 is your OTP for INR 1200 on card XX4321")!!
        assertFalse(result.contains("654321"))
        assertTrue(result.contains("1200"))
    }

    @Test
    fun dropsPureAndAmbiguousOtp() {
        assertNull(sanitizeSmsBody("Login OTP is 654321"))
        assertNull(sanitizeSmsBody("Use OTP for INR 1200 on card 4321. Code: 654321"))
        assertNull(sanitizeSmsBody("OTP 654321 for INR 1200 on card XX4321. Alternate code 987654"))
    }

    @Test
    fun handlesCommonBankLayouts() {
        listOf(
                "123456 is the OTP for your transaction of INR 2,500.00 at AMAZON on card ending 4321.",
                "Your OTP for txn of INR 2500.00 at AMAZON on HDFC Bank Card ending 4321 is 123456. Do not share.",
                "Use 123456 as OTP to pay INR 2500 on card XX4321 at AMAZON.",
            )
            .forEach { body ->
                val out = sanitizeSmsBody(body) ?: error("dropped: $body")
                assertFalse(out, out.contains("123456"))
                assertTrue(out, out.contains("AMAZON") && out.contains("4321"))
                assertTrue(out, out.contains("[REDACTED]"))
                assertEquals(out, sanitizeSmsBody(out))
            }
    }

    @Test
    fun keepsConfirmedTransactionUnchanged() {
        val body = "INR 1200 spent on card XX4321 at AMAZON"
        assertEquals(body, sanitizeSmsBody(body))
    }
}
