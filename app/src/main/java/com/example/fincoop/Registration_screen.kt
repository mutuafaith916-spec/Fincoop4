package com.example.fincoop

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class RegisterActivity : AppCompatActivity() {

    private lateinit var fullName: TextInputEditText
    private lateinit var email: TextInputEditText
    private lateinit var phone: TextInputEditText
    private lateinit var password: TextInputEditText
    private lateinit var confirmPassword: TextInputEditText
    private lateinit var terms: CheckBox
    private lateinit var registerButton: Button
    private lateinit var loginLink: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration_screen)

        fullName = findViewById(R.id.etFullName)
        email = findViewById(R.id.etEmail)
        phone = findViewById(R.id.etPhone)
        password = findViewById(R.id.etPassword)
        confirmPassword = findViewById(R.id.etConfirmPassword)
        terms = findViewById(R.id.cbTerms)
        registerButton = findViewById(R.id.btnRegister)
        loginLink = findViewById(R.id.txtLogin)

        registerButton.setOnClickListener {

            val name = fullName.text.toString().trim()
            val emailText = email.text.toString().trim()
            val phoneText = phone.text.toString().trim()
            val pass = password.text.toString()
            val confirmPass = confirmPassword.text.toString()

            when {
                name.isEmpty() ->
                    showMessage("Enter Full Name")

                emailText.isEmpty() ->
                    showMessage("Enter Email Address")

                phoneText.isEmpty() ->
                    showMessage("Enter Phone Number")

                pass.isEmpty() ->
                    showMessage("Enter Password")

                pass != confirmPass ->
                    showMessage("Passwords do not match")

                !terms.isChecked ->
                    showMessage("Accept Terms & Conditions")

                else ->
                    showMessage("Registration Successful")
            }
        }

        loginLink.setOnClickListener {
            finish()
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}