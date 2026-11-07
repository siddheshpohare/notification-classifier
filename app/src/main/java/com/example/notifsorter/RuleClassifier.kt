package com.example.notifsorter

import kotlin.math.exp

object Decision {
    const val SILENCE = 0
    const val IMPORTANT = 1
    const val UNSURE = 2   // shown, flagged so you can label it
}

data class Result(val score: Float, val decision: Int)

/**
 * Phase-1 classifier: keyword + sender-ID rules (no model needed).
 * Pure Kotlin, so it is unit-testable off-device.
 * Replace later with the fine-tuned model behind the same classify() call.
 */
object RuleClassifier {

    private class Rule(pattern: String, val weight: Double) {
        val regex = Regex(pattern)
    }

    // Evidence for IMPORTANT
    private val important = listOf(
        Rule("""\botp\b|one[- ]time password|verification code|\bcvv\b""", 3.0),
        Rule("""credited|debited|withdrawn|deposited""", 2.5),
        Rule("""payment received|paid you|received rs|money received|sent you rs|sent you ₹""", 2.0),
        Rule("""security alert|password was|sign-in|new device|suspicious""", 2.5),
        Rule("""\bexams?\b|timetable|hall ticket|admit card|\bresults?\b|assignment|attendance|deadline|\bviva\b|practical|semester|\bfees?\b|scholarship|revaluation|marks""", 2.0),
        Rule("""interview|shortlisted|offer letter|placement|you are selected|selected for""", 2.0),
        Rule("""\bpnr\b|ticket is confirmed|booking confirmed|chart prepared""", 2.0),
        Rule("""urgent|\basap\b|jaldi|zaroori|zaruri|turant""", 1.5),
        Rule("""\bdue (on|date|by)|statement|bill (generated|due)|\bkyc\b|account (freeze|blocked|suspended)""", 1.5),
        Rule("""meeting|reminder|appointment|rescheduled|payment failed|refund|overdue""", 1.5),
    )

    // Evidence for JUNK (positive numbers; subtracted)
    private val promo = listOf(
        Rule("""\boffers?\b(?! letter)|\bcashback\b|\bsale\b|limited (time|period)|hurry|ends tonight|last chance|don.?t miss""", 2.0),
        Rule("""\d+\s?%\s?off|flat\s?(rs\.?|₹)?\s?\d+|up ?to \d+\s?%|upto\s?\d+""", 2.5),
        Rule("""pre-?approved|pre-?selected|(personal|instant|home) loan""", 2.5),
        Rule("""(apply|register|claim|order|shop|buy|download|invite|play|upgrade) now|click (here|to)""", 2.0),
        Rule("""scratch card|win up to|win a |win free|promo code|use code|kharid|dhamaka|chhoot|bachat""", 2.0),
        Rule("""new followers?|liked your|started following|new video|you may like|recommended (for )?you|trending|newsletter|digest|webinar|come back|lives are|jobs? (for you|near you|match)|hiring for|internships? matching|apply to \d+""", 2.0),
        // weak words: one alone only makes it "unsure"
        Rule("""\bfree\b|\bdeals?\b|\brewards?\b|discount|coupon""", 1.0),
    )

    // Indian SMS sender IDs: -P promo, -S service, -T transactional, -G government
    private val senderId = Regex("""^(?:[a-z]{2}-)?[a-z0-9]{3,10}-([pstg])$""")

    // Never silence these unless there is clear promo evidence too.
    private val forcedImportant = Regex("""\botp\b|credited|debited|security alert""")

    fun classify(title: String, body: String): Result {
        val t = title.trim().lowercase()
        val blob = t + " " + body.lowercase()

        var imp = 0.0
        var pro = 0.0
        for (r in important) if (r.regex.containsMatchIn(blob)) imp += r.weight
        for (r in promo) if (r.regex.containsMatchIn(blob)) pro += r.weight

        senderId.find(t)?.let {
            when (it.groupValues[1]) {
                "p" -> pro += 3.0
                "s", "t" -> imp += 2.5
                "g" -> imp += 1.5
            }
        }

        val score = (1.0 / (1.0 + exp(-(imp - pro)))).toFloat()
        val forced = pro == 0.0 && forcedImportant.containsMatchIn(blob)
        val decision = when {
            forced || score >= 0.6f -> Decision.IMPORTANT
            score <= 0.25f -> Decision.SILENCE
            else -> Decision.UNSURE
        }
        return Result(score, decision)
    }
}
