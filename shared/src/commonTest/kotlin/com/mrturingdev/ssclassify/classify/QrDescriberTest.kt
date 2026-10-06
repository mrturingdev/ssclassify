package com.mrturingdev.ssclassify.classify

import kotlin.test.Test
import kotlin.test.assertEquals

class QrDescriberTest {

    private fun describe(payload: String) = QrDescriber.describe(payload).description

    @Test
    fun commonCodesGetReadableDescriptions() {
        assertEquals("Wi-Fi QR for network: Home 5G", describe("WIFI:T:WPA;S:Home 5G;P:secret;;"))
        assertEquals("Wi-Fi QR for network: a;b", describe("""WIFI:S:a\;b;T:WPA;P:x;;"""))
        assertEquals("Authenticator App QR (2FA)", describe("otpauth://totp/Example:sita?secret=ABC"))
        assertEquals("QR Code linking to: example.com", describe("https://example.com/menu?table=4"))
        assertEquals("Instagram QR Code", describe("https://instagram.com/someone"))
        assertEquals("Phone QR: +9779800000000", describe("tel:+9779800000000"))
        assertEquals("QR Code: CTIA3P", describe("CTIA3P"))
    }

    @Test
    fun paymentCodesBecomePaymentQrsWithTheirPayee() {
        val result = QrDescriber.describe("""{"eSewa_id":"9800000000","name":"Sita Shrestha"}""")
        assertEquals(QrResult(PaymentQrParser.SUB_CATEGORY, "eSewa payment QR · Sita Shrestha", """{"eSewa_id":"9800000000","name":"Sita Shrestha"}""", "Sita Shrestha"), result)
    }
}
