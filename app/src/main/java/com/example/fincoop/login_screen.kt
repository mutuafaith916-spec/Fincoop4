package com.example.fincoop

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: FincoopRepository) : ViewModel() {
    val loginState = MutableLiveData<Resource<String>>()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            loginState.value = Resource.Loading()
            val result = repository.loginRemote(email, password)
            loginState.value = result
        }
    }
}

class LoginActivity : SecureActivity() {

    private val repository by lazy { FincoopRepository(this) }
    private val viewModel: LoginViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LoginViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_screen)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (!validateInput(email, password)) return@setOnClickListener

            viewModel.login(email, password)
        }

        findViewById<TextView>(R.id.txtCreateAccount).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Invalid Email"
            return false
        }
        if (password.isEmpty() || password.length < 4) {
            etPassword.error = "Password too short"
            return false
        }
        return true
    }

    private fun observeViewModel() {
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        
        viewModel.loginState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> progressBar.visibility = View.VISIBLE
                is Resource.Success -> {
                    progressBar.visibility = View.GONE
                    startActivity(Intent(this, DashboardActivity::class.java).apply {
                        putExtra("USER_EMAIL", resource.data)
                    })
                    finish()
                }
                is Resource.Error -> {
                    progressBar.visibility = View.GONE
                    Snackbar.make(findViewById(android.R.id.content), resource.message ?: "Login Failed", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }
}
