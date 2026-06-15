package com.example.fincoop

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class RegisterViewModel(private val repository: FincoopRepository) : ViewModel() {
    val registerState = MutableLiveData<Resource<String>>()

    fun register(email: String, password: String) {
        viewModelScope.launch {
            registerState.value = Resource.Loading()
            val result = repository.registerRemote(email, password)
            registerState.value = result
        }
    }
}

class RegisterActivity : AppCompatActivity() {

    private val repository by lazy { FincoopRepository(this) }
    private val viewModel: RegisterViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RegisterViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration_screen)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        val registerButton = findViewById<Button>(R.id.btnRegister)
        val loginLink = findViewById<TextView>(R.id.txtLogin)

        registerButton.setOnClickListener {
            if (validateForm()) {
                val email = findViewById<TextInputEditText>(R.id.etEmail).text.toString()
                val pass = findViewById<TextInputEditText>(R.id.etPassword).text.toString()
                viewModel.register(email, pass)
            }
        }

        loginLink.setOnClickListener {
            finish()
        }
    }

    private fun observeViewModel() {
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        viewModel.registerState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> progressBar.visibility = View.VISIBLE
                is Resource.Success -> {
                    progressBar.visibility = View.GONE
                    showSnackbar(resource.data ?: "Success")
                    findViewById<View>(android.R.id.content).postDelayed({ finish() }, 2000)
                }
                is Resource.Error -> {
                    progressBar.visibility = View.GONE
                    showSnackbar(resource.message ?: "Error")
                }
            }
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
