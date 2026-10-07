package `in`.voxagent.mobile.sms

import org.junit.Assert.*
import org.junit.Test

class OtpDetectorTest {
    @Test fun preservesFinancialContextWithoutOtp() {
        val body = "OTP is 654321 for transaction of INR 1,250 on credit card XX4321 at AMAZON"
        val result = sanitizeSmsBody(body)!!
        assertFalse(result.contains("654321"))
        assertTrue(result.contains("1,250"))
        assertTrue(result.contains("XX4321"))
        assertTrue(result.contains("AMAZON"))
    }
    @Test fun supportsCodeBeforeLabel() {
        val result = sanitizeSmsBody("654321 is your OTP for INR 1200 on card XX4321")!!
        assertFalse(result.contains("654321"))
        assertTrue(result.contains("1200"))
    }
    @Test fun dropsPureAndAmbiguousOtp() {
        assertNull(sanitizeSmsBody("Login OTP is 654321"))
        assertNull(sanitizeSmsBody("Use OTP for INR 1200 on card 4321. Code: 654321"))
        assertNull(sanitizeSmsBody("OTP 654321 for INR 1200 on card XX4321. Alternate code 987654"))
    }
    @Test fun keepsConfirmedTransactionUnchanged() {
        val body = "INR 1200 spent on card XX4321 at AMAZON"
        assertEquals(body, sanitizeSmsBody(body))
    }
}
