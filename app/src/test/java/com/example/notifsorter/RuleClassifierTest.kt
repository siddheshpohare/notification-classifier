package com.example.notifsorter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RuleClassifierTest {
    private fun d(title: String, body: String) = RuleClassifier.classify(title, body).decision

    @Test fun bankCreditIsImportant() =
        assertEquals(Decision.IMPORTANT, d("HDFC Bank Alert", "Rs 2,300.00 credited to A/c XX4521"))

    @Test fun otpIsImportant() =
        assertEquals(Decision.IMPORTANT, d("VM-BANKOT-T", "839201 is your OTP. Do not share."))

    @Test fun loanOfferIsSilenced() =
        assertEquals(Decision.SILENCE, d("HDFC Bank Offers", "Pre-approved loan of Rs 5,00,000! Apply now."))

    @Test fun promoSenderSuffixIsSilenced() =
        assertEquals(Decision.SILENCE, d("TM-ZOMATO-P", "Hungry? Get free delivery today"))

    @Test fun friendMessageWithWeakWordIsNotSilenced() =
        assertNotEquals(Decision.SILENCE, d("Rohan", "are you free tomorrow evening?"))

    @Test fun offerLetterIsNotTreatedAsPromo() =
        assertEquals(Decision.IMPORTANT, d("Gmail Careers", "Congratulations! Your offer letter is attached"))

    @Test fun liveClassJoinNowIsNotSilenced() =
        assertNotEquals(Decision.SILENCE, d("Unacademy", "Class starts in 10 minutes, join now"))
}
