package com.example.notifsorter

object Pipeline {

    /** Same sender + similar title => same key (digits stripped so "Order 123" == "Order 456"). */
    fun normKey(pkg: String, title: String): String =
        pkg + "|" + title.lowercase().replace(Regex("\\d+"), "#").trim()

    /**
     * Decide a notification. Your earlier corrections (instant rules) win over the classifier.
     * Swap RuleClassifier for the fine-tuned model here later.
     */
    fun decide(store: Store, pkg: String, title: String, body: String): Result {
        val o = store.override(normKey(pkg, title))
        if (o != null) {
            return if (o == 1) Result(1f, Decision.IMPORTANT) else Result(0f, Decision.SILENCE)
        }
        return RuleClassifier.classify(title, body)
    }
}
