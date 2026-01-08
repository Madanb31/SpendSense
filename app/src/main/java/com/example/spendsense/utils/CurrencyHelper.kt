package com.example.spendsense.utils

import android.content.Context
import java.text.NumberFormat
import java.util.Locale

object CurrencyHelper {

    // Get the saved symbol (₹, $, etc.)
    fun getSymbol(context: Context): String {
        val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        return prefs.getString("currency", "₹") ?: "₹"
    }

    // NEW: Format amount with Indian Commas (e.g. 1,50,000)
    fun format(context: Context, amount: Double): String {
        val symbol = getSymbol(context)

        // Use "en", "IN" for Indian numbering system (lakhs/crores)
        val format = NumberFormat.getInstance(Locale("en", "IN"))

        // Settings: No decimal places (like your previous code)
        format.maximumFractionDigits = 0

        return "$symbol${format.format(amount)}"
    }
}