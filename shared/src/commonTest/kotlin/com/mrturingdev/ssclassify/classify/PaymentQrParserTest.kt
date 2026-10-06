package com.mrturingdev.ssclassify.classify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Payloads are synthetic (fake merchants) but shaped like real Nepali QRs
 * decoded from screenshots, with CRCs computed independently.
 */
class PaymentQrParserTest {

    private val fonepayDynamic =
        "00020101021226320011fonepay.com011322220000000015204591253035245406699.005802NP" +
            "5914EVEREST BAKERY6009Kathmandu62080104ORD16304A9DB"

    /** NepalPay + Alipay + UnionPay + an unknown network on one code. */
    private val multiNetwork =
        "0002010102111516999900000000000027360032c81d4e2ebcf211e6869b7df92533d2db29310022NCHL00010001O000012181" +
            "0101X49190010com.alipay0101Y5204591253035245802NP5916HIMALAYAN PHARMA6008Kirtipur6304EE80"

    @Test
    fun fonepayMerchantQrWithAmount() {
        val qr = PaymentQrParser.parse(fonepayDynamic)
        assertEquals(PaymentQr("Fonepay", "EVEREST BAKERY", "Kathmandu", "NPR 699.00"), qr)
        assertEquals("Fonepay payment QR · EVEREST BAKERY, Kathmandu · NPR 699.00", qr?.description)
    }

    @Test
    fun domesticNetworkWinsOnMultiNetworkCodes() {
        assertEquals(PaymentQr("NepalPay", "HIMALAYAN PHARMA", "Kirtipur"), PaymentQrParser.parse(multiNetwork))
    }

    @Test
    fun aCorruptedCodeIsNotTrusted() {
        assertNull(PaymentQrParser.parse(fonepayDynamic.dropLast(4) + "0000"))
        assertNull(PaymentQrParser.parse("000201 is just text"))
    }

    @Test
    fun walletAndBankAccountJsonNeverExposeNumbers() {
        assertEquals(
            PaymentQr("eSewa", "Sita Shrestha"),
            PaymentQrParser.parse("""{"eSewa_id":"9800000000","name":"Sita Shrestha"}"""),
        )
        val bank = PaymentQrParser.parse("""{"accountNumber":"09000012345","accountName":"Ram Thapa","bankCode":"NIBL"}""")
        assertEquals(PaymentQr("Bank account", "Ram Thapa"), bank)
        assertEquals(false, bank!!.description.contains("09000012345"))
    }

    @Test
    fun upiLinks() {
        assertEquals(
            PaymentQr("UPI", "Chai Point", amount = "INR 40.00"),
            PaymentQrParser.parse("upi://pay?pa=chaipoint@okaxis&pn=Chai%20Point&am=40.00&cu=INR"),
        )
    }

    @Test
    fun nonPaymentCodesAreIgnored() {
        assertNull(PaymentQrParser.parse("WIFI:T:WPA;S:Home;P:secret;;"))
        assertNull(PaymentQrParser.parse("otpauth://totp/Example:sita?secret=ABC"))
        assertNull(PaymentQrParser.parse("""{"code":"P1","properties":{}}"""))
        assertNull(PaymentQrParser.parse("CTIA3P"))
    }
}
