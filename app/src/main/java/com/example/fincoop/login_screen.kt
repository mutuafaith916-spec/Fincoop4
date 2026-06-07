package com.example.fincoop

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var txtForgotPassword: TextView
    private lateinit var txtCreateAccount: TextView
    private lateinit var cbRememberMe: CheckBox
    private lateinit var themeSwitch: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_screen)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        progressBar = findViewById(R.id.progressBar)
        txtForgotPassword = findViewById(R.id.txtForgotPassword)
        txtCreateAccount = findViewById(R.id.txtCreateAccount)
        cbRememberMe = findViewById(R.id.cbRememberMe)
        themeSwitch = findViewById(R.id.themeSwitch)

        setupThemeSwitch()

        btnLogin.setOnClickListener {

            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty()) {
                etEmail.error = "Enter Email"
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Invalid Email"
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                etPassword.error = "Enter Password"
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE

            btnLogin.postDelayed({
                progressBar.visibility = View.GONE

                Toast.makeText(
                    this,
                    "Login Successful",
                    Toast.LENGTH_SHORT,

                ).show()

                startActivity(
                    Intent(
                        this,
                        DashboardActivity::class.java,
                    ).apply {
                        putExtra("USER_EMAIL", email)
                    }
                )

                finish()

            }, 2000)
        }

        txtForgotPassword.setOnClickListener {
            Toast.makeText(
                this,
                "Forgot Password Clicked",
                Toast.LENGTH_SHORT
            ).show()
        }

        txtCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun setupThemeSwitch() {
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        themeSwitch.isChecked = currentMode == AppCompatDelegate.MODE_NIGHT_YES

        themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
    }
}
