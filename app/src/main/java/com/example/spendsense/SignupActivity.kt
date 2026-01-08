package com.example.spendsense

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.spendsense.database.AppDatabase
import com.example.spendsense.database.User
import kotlinx.coroutines.launch

class SignupActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        database = AppDatabase.getDatabase(this)

        val nameInput = findViewById<EditText>(R.id.et_name)
        val phoneInput = findViewById<EditText>(R.id.et_phone)
        val emailInput = findViewById<EditText>(R.id.et_email)
        val passwordInput = findViewById<EditText>(R.id.et_password) // New
        val confirmPasswordInput = findViewById<EditText>(R.id.et_confirm_password) // New
        val phoneRadio = findViewById<RadioButton>(R.id.radio_phone)
        val emailRadio = findViewById<RadioButton>(R.id.radio_email)
        val getStartedButton = findViewById<Button>(R.id.btn_get_started)

        phoneInput.visibility = android.view.View.VISIBLE
        emailInput.visibility = android.view.View.GONE

        phoneRadio.setOnClickListener {
            phoneInput.visibility = android.view.View.VISIBLE
            emailInput.visibility = android.view.View.GONE
        }

        emailRadio.setOnClickListener {
            phoneInput.visibility = android.view.View.GONE
            emailInput.visibility = android.view.View.VISIBLE
        }

        getStartedButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString() // New
            val confirmPassword = confirmPasswordInput.text.toString() // New

            // --- VALIDATION ---
            if (name.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (phoneRadio.isChecked && phone.isEmpty()) {
                Toast.makeText(this, "Please enter your phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (emailRadio.isChecked && email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveUserToDatabase(name, phone, email, password, if (phoneRadio.isChecked) "phone" else "email")
        }
    }

    private fun saveUserToDatabase(name: String, phone: String, email: String, password: String, loginMethod: String) {
        lifecycleScope.launch {
            try {
                // 1. Check if user already exists
                val existingUser = if (loginMethod == "email") database.userDao().getUserByEmail(email) else database.userDao().getUserByPhone(phone)
                if (existingUser != null) {
                    Toast.makeText(this@SignupActivity, "An account with this ${loginMethod} already exists.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // 2. HASH THE PASSWORD FIRST!
                val hashedPassword = com.example.spendsense.utils.SecurityHelper.hashPassword(password)

                // 3. Create User object with HASHED password
                val user = User(
                    name = name,
                    email = email,
                    phone = phone,
                    password = hashedPassword, // Store the hash, NOT the plain text
                    loginMethod = loginMethod
                )

                // 4. Save to Database
                val userId = database.userDao().insertUser(user)

                // 5. Save Session
                val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                editor.putInt("userId", userId.toInt())
                editor.putBoolean("isLoggedIn", true)
                editor.apply()

                Toast.makeText(this@SignupActivity, "Welcome, $name!", Toast.LENGTH_SHORT).show()

                // 6. Navigate
                val intent = Intent(this@SignupActivity, MainActivity::class.java)
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                Toast.makeText(this@SignupActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}