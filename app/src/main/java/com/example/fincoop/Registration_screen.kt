package com.example.fincoop

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration_screen)

        val registerButton = findViewById<Button>(R.id.btnRegister)
        val loginLink = findViewById<TextView>(R.id.txtLogin)

        registerButton.setOnClickListener {
            if (validateForm()) {
                showSnackbar("Registration Successful! Please login.")
                it.postDelayed({ finish() }, 1500)
            }
        }

        loginLink.setOnClickListener {
            finish()
        }
    }

    private fun validateForm(): Boolean {
        val name = findViewById<TextInputEditText>(R.id.etFullName).text.toString()
        val email = findViewById<TextInputEditText>(R.id.etEmail).text.toString()
        val pass = findViewById<TextInputEditText>(R.id.etPassword).text.toString()
        val confirmPass = findViewById<TextInputEditText>(R.id.etConfirmPassword).text.toString()
        val terms = findViewById<CheckBox>(R.id.cbTerms)

        if (name.isEmpty()) { showSnackbar("Enter Full Name"); return false }
        if (email.isEmpty()) { showSnackbar("Enter Email"); return false }
        if (pass.length < 4) { showSnackbar("Password too short"); return false }
        if (pass != confirmPass) { showSnackbar("Passwords mismatch"); return false }
        if (!terms.isChecked) { showSnackbar("Accept Terms & Conditions"); return false }
        
        return true
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
    }
}
