package com.example.spendsense.utils

import android.content.Context

object CurrencyHelper {
    fun getSymbol(context: Context): String {
        val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        return prefs.getString("currency", "₹") ?: "₹"
    }
}