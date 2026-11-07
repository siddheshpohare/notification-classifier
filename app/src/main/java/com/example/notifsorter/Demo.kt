package com.example.notifsorter

/** Fake notifications so you can try the app without waiting for real ones. */
object Demo {
    private val samples = listOf(
        Triple("HDFC Bank Alert", "Rs 2,300.00 credited to A/c XX4521 on 05-Nov.", "HDFC Bank"),
        Triple("HDFC Bank Offers", "Pre-approved loan of Rs 5,00,000! Apply now.", "HDFC Bank"),
        Triple("Dean Academics", "Fee payment last date is 10 Nov. Pay to avoid late fine.", "Gmail"),
        Triple("Coursera", "Flash sale: courses from Rs 449. Ends tonight!", "Gmail"),
        Triple("Payment received", "Neha paid you Rs 500.", "PhonePe"),
        Triple("Scratch card!", "Win up to Rs 1000 cashback. Claim now.", "PhonePe"),
        Triple("VM-BANKOT-T", "839201 is your OTP. Do not share with anyone.", "Messages"),
        Triple("Lives full", "Come back and play now!", "Candy Crush"),
        Triple("Rohan", "are you free tomorrow evening?", "Messages"),
    )

    fun add(store: Store) {
        val now = System.currentTimeMillis()
        samples.forEachIndexed { i, (title, body, app) ->
            val pkg = "demo." + app.lowercase().replace(" ", "")
            val res = Pipeline.decide(store, pkg, title, body)
            store.insert(
                Rec(
                    nkey = Pipeline.normKey(pkg, title), pkg = pkg, app = app, title = title, body = body,
                    ts = now - i * 1000L, score = res.score, decision = res.decision, hidden = false,
                )
            )
        }
    }
}
