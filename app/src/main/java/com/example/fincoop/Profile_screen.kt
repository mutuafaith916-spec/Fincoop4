package com.example.fincoop

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_screen)

        setupUserInfo()
        setupClickListeners()
    }

    private fun setupUserInfo() {
        val email = intent.getStringExtra("USER_EMAIL") ?: "user@example.com"
        val name = email.substringBefore("@").replaceFirstChar { it.uppercase() }
        
        findViewById<TextView>(R.id.txtFullName).text = name
        findViewById<TextView>(R.id.txtEmail).text = email
    }

    private fun setupClickListeners() {
        findViewById<Button>(R.id.btnEditProfile).setOnClickListener {
            Toast.makeText(this, "Edit Profile feature is currently unavailable", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnChangePassword).setOnClickListener {
            Toast.makeText(this, "Change Password feature is currently unavailable", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
