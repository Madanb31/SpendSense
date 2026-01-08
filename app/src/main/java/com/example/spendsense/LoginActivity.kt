package com.example.spendsense

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.spendsense.database.AppDatabase
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        database = AppDatabase.getDatabase(this)

        val phoneInput = findViewById<EditText>(R.id.et_phone)
        val emailInput = findViewById<EditText>(R.id.et_email)
        val passwordInput = findViewById<EditText>(R.id.et_password)
        val phoneRadio = findViewById<RadioButton>(R.id.radio_phone)
        val emailRadio = findViewById<RadioButton>(R.id.radio_email)
        val loginButton = findViewById<Button>(R.id.btn_login)
        val signupText = findViewById<TextView>(R.id.tv_signup)

        // Initially show phone input
        phoneInput.visibility = android.view.View.VISIBLE
        emailInput.visibility = android.view.View.GONE

        // Handle radio button selection
        phoneRadio.setOnClickListener {
            phoneInput.visibility = android.view.View.VISIBLE
            emailInput.visibility = android.view.View.GONE
        }

        emailRadio.setOnClickListener {
            phoneInput.visibility = android.view.View.GONE
            emailInput.visibility = android.view.View.VISIBLE
        }

        // Handle Login button
        loginButton.setOnClickListener {
            val phone = phoneInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if ((phoneRadio.isChecked && phone.isEmpty()) || (emailRadio.isChecked && email.isEmpty()) || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            performLogin(if (phoneRadio.isChecked) phone else email, password, phoneRadio.isChecked)
        }

        // Handle "Don't have an account? Sign Up" text
        signupText.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
        }
    }

    private fun performLogin(identifier: String, password: String, isPhoneLogin: Boolean) {
        lifecycleScope.launch {
            try {
                // 1. Find user in database
                val user = if (isPhoneLogin) {
                    database.userDao().getUserByPhone(identifier)
                } else {
                    database.userDao().getUserByEmail(identifier)
                }

                if (user == null) {
                    Toast.makeText(this@LoginActivity, "No account found", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // 2. HASH INPUT PASSWORD FIRST
                val hashedInput = com.example.spendsense.utils.SecurityHelper.hashPassword(password)

                // 3. COMPARE HASHES (Stored Hash vs Input Hash)
                if (user.password != hashedInput) {
                    Toast.makeText(this@LoginActivity, "Incorrect password", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // 4. Login Successful - Save Session
                val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                editor.putInt("userId", user.id)
                editor.putBoolean("isLoggedIn", true)
                editor.apply()

                Toast.makeText(this@LoginActivity, "Welcome back, ${user.name}!", Toast.LENGTH_SHORT).show()

                // 5. Navigate
                val intent = Intent(this@LoginActivity, MainActivity::class.java)
                startActivity(intent)
                finishAffinity()

            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}