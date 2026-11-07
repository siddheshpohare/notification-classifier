package com.example.notifsorter

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    /** false = learning mode: classify and log, but never hide anything. */
    fun silenceEnabled(c: Context) = sp(c).getBoolean("silence", false)
    fun setSilenceEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("silence", v).apply()
}
