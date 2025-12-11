package com.example.spendsense

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.spendsense.database.AppDatabase
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase

    private var selectedCurrency: String = "₹" // Default
    private var userId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Enable back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        // Initialize database
        database = AppDatabase.getDatabase(this)

        // Get userId from SharedPreferences
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        userId = sharedPreferences.getInt("userId", 0)

        // Load user data from database
        loadUserData()

        // App version
        val versionText = findViewById<TextView>(R.id.tv_app_version)
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            versionText.text = "Version $versionName"
        } catch (e: Exception) {
            versionText.text = "Version 1.0.0"
        }

        // Logout button
        val logoutButton = findViewById<Button>(R.id.btn_logout)
        logoutButton.setOnClickListener {
            showLogoutDialog()
        }

        // Currency selection - NOW WORKING
        val currencyText = findViewById<TextView>(R.id.setting_currency)

        // Load saved currency to display
        val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        selectedCurrency = prefs.getString("currency", "₹") ?: "₹"
        currencyText.text = "💱  Currency ($selectedCurrency)"

        currencyText.setOnClickListener {
            showCurrencyDialog(currencyText)
        }

        // About
        findViewById<TextView>(R.id.setting_about).setOnClickListener {
            showAboutDialog()
        }

        // Privacy Policy
        findViewById<TextView>(R.id.setting_privacy).setOnClickListener {
            Toast.makeText(this, "Privacy Policy (Coming Soon!)", Toast.LENGTH_SHORT).show()
        }

        // Clear Data
        findViewById<TextView>(R.id.setting_clear_data).setOnClickListener {
            showClearDataDialog()
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                val user = database.userDao().getUserById(userId)

                if (user != null) {
                    // Display user info
                    findViewById<TextView>(R.id.tv_user_name).text = user.name
                    findViewById<TextView>(R.id.tv_user_contact).text =
                        if (user.loginMethod == "phone") user.phone else user.email
                } else {
                    findViewById<TextView>(R.id.tv_user_name).text = "User"
                    findViewById<TextView>(R.id.tv_user_contact).text = "No contact info"
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Error loading user data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        // Clear login status
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean("isLoggedIn", false)
        editor.apply()

        // Show message
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()

        // Go to Login screen
        val intent = Intent(this, LoginActivity::class.java) // CHANGE THIS
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About SpendSense")
            .setMessage("SpendSense v1.0.0\n\nA simple and elegant expense tracker to help you manage your money smartly.\n\n© 2025 SpendSense")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showClearDataDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Data")
            .setMessage("This will delete all your transactions and budgets. This action cannot be undone. Are you sure?")
            .setPositiveButton("Clear") { _, _ ->
                clearAllData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCurrencyDialog(textView: TextView) {
        val currencies = arrayOf("₹ (INR)", "$ (USD)", "€ (EUR)", "£ (GBP)", "¥ (JPY)")
        val symbols = arrayOf("₹", "$", "€", "£", "¥")

        // Find current selection index
        var checkedItem = symbols.indexOf(selectedCurrency)
        if (checkedItem == -1) checkedItem = 0 // Default to first if not found

        AlertDialog.Builder(this)
            .setTitle("Select Currency")
            .setSingleChoiceItems(currencies, checkedItem) { dialog, which ->
                // Update variable
                selectedCurrency = symbols[which]

                // Save to SharedPreferences
                val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                val editor = prefs.edit()
                editor.putString("currency", selectedCurrency)
                editor.apply()

                // Update UI text immediately
                textView.text = "💱  Currency ($selectedCurrency)"

                Toast.makeText(this, "Currency set to $selectedCurrency", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun clearAllData() {
        lifecycleScope.launch {
            try {
                // Delete all user's transactions
                database.transactionDao().deleteAllTransactions(userId)

                Toast.makeText(
                    this@SettingsActivity,
                    "All data cleared successfully",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    "Error clearing data: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}