package com.example.spendsense

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.spendsense.database.AppDatabase
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private var userId: Int = 0
    private var selectedCurrency: String = "₹"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        database = AppDatabase.getDatabase(this)
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        userId = sharedPreferences.getInt("userId", 0)
        selectedCurrency = sharedPreferences.getString("currency", "₹") ?: "₹"

        // Load User Data
        loadUserData()

        // App Version
        val versionText = findViewById<TextView>(R.id.tv_app_version)
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            versionText.text = versionName
        } catch (e: Exception) {
            versionText.text = "1.0.0"
        }

        // Logout
        findViewById<Button>(R.id.btn_logout).setOnClickListener {
            showLogoutDialog()
        }

        // Currency Setting
        val currencyText = findViewById<TextView>(R.id.setting_currency)
        currencyText.text = "💱  Currency ($selectedCurrency)"
        currencyText.setOnClickListener {
            showCurrencyDialog(currencyText)
        }

        // Theme Setting (Dark Mode)
        findViewById<TextView>(R.id.setting_theme).setOnClickListener {
            showThemeDialog()
        }

        // About
        findViewById<TextView>(R.id.setting_about).setOnClickListener {
            showAboutDialog()
        }

        // Clear Data
        findViewById<TextView>(R.id.setting_clear_data).setOnClickListener {
            showClearDataDialog()
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            val user = database.userDao().getUserById(userId)
            if (user != null) {
                findViewById<TextView>(R.id.tv_user_name).text = user.name
                findViewById<TextView>(R.id.tv_user_contact).text =
                    if (user.loginMethod == "phone") user.phone else user.email
            }
        }
    }

    private fun showCurrencyDialog(textView: TextView) {
        val currencies = arrayOf("₹ (INR)", "$ (USD)", "€ (EUR)", "£ (GBP)", "¥ (JPY)")
        val symbols = arrayOf("₹", "$", "€", "£", "¥")
        var checkedItem = symbols.indexOf(selectedCurrency)
        if (checkedItem == -1) checkedItem = 0

        AlertDialog.Builder(this)
            .setTitle("Select Currency")
            .setSingleChoiceItems(currencies, checkedItem) { dialog, which ->
                selectedCurrency = symbols[which]
                val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                prefs.edit().putString("currency", selectedCurrency).apply()

                textView.text = "𒒱  Currency ($selectedCurrency)"
                Toast.makeText(this, "Currency set to $selectedCurrency", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showThemeDialog() {
        val themes = arrayOf("Light", "Dark", "System Default")
        // Logic to determine checked item (omitted for brevity, keep existing)

        AlertDialog.Builder(this)
            .setTitle("Choose Theme")
            .setSingleChoiceItems(themes, -1) { dialog, which ->
                val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                val editor = prefs.edit()

                when (which) {
                    0 -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        editor.putInt("theme_mode", AppCompatDelegate.MODE_NIGHT_NO)
                    }
                    1 -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        editor.putInt("theme_mode", AppCompatDelegate.MODE_NIGHT_YES)
                    }
                    2 -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                        editor.putInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    }
                }
                editor.apply()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                prefs.edit().putBoolean("isLoggedIn", false).apply()

                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClearDataDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Data")
            .setMessage("This will delete ALL transactions permanently. Are you sure?")
            .setPositiveButton("Clear") { _, _ ->
                lifecycleScope.launch {
                    database.transactionDao().deleteAllTransactions(userId)
                    Toast.makeText(this@SettingsActivity, "Data Cleared", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About SpendSense")
            .setMessage("SpendSense v1.0.0\n\nSmart Expense Tracker\n\n© 2025 SpendSense")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}