package com.example.fincoop

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class DashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        setupWelcomeMessage()
        setupProfileHeader()
        setupQuickActions()
        setupBottomNavigation()
    }

    private fun setupWelcomeMessage() {
        val email = intent.getStringExtra("USER_EMAIL") ?: "User"
        val name = email.substringBefore("@").replaceFirstChar { it.uppercase() }
        findViewById<TextView>(R.id.txtWelcome).text = getString(R.string.welcome_user, name)
    }

    private fun setupProfileHeader() {
        findViewById<android.view.View>(R.id.layoutProfileHeader).setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java).apply {
                putExtra("USER_EMAIL", this@DashboardActivity.intent.getStringExtra("USER_EMAIL"))
            }
            startActivity(intent)
        }
    }

    private fun setupQuickActions() {
        findViewById<Button>(R.id.btnDeposit).setOnClickListener {
            showToast("Deposit feature is currently unavailable")
        }

        findViewById<Button>(R.id.btnWithdraw).setOnClickListener {
            showToast("Withdrawal feature is currently unavailable")
        }

        findViewById<Button>(R.id.btnApplyLoan).setOnClickListener {
            startActivity(Intent(this, LoanApplicationActivity::class.java))
        }

        findViewById<Button>(R.id.btnTransfer).setOnClickListener {
            showToast("Transfer feature is currently unavailable")
        }

        findViewById<android.view.View>(R.id.cardRecentTransactions).setOnClickListener {
            startActivity(Intent(this, TransactionHistoryActivity::class.java))
        }
    }

    private fun setupBottomNavigation() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Already on home
                    true
                }
                R.id.nav_transactions -> {
                    startActivity(Intent(this, TransactionHistoryActivity::class.java))
                    true
                }
                R.id.nav_profile -> {
                    val intent = Intent(this, ProfileActivity::class.java).apply {
                        putExtra("USER_EMAIL", this@DashboardActivity.intent.getStringExtra("USER_EMAIL"))
                    }
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
