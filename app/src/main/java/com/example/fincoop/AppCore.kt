package com.example.fincoop

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

// --- Models ---
data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val type: TransactionType,
    val amount: Double,
    val date: Date = Date(),
    val status: String = "Completed",
    val description: String
)

enum class TransactionType {
    DEPOSIT, WITHDRAWAL, TRANSFER, LOAN_REPAYMENT
}

sealed class Resource<T>(val data: T? = null, val message: String? = null) {
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)
    class Loading<T> : Resource<T>()
}

// --- Data Repository (Networking + Local Storage) ---
class FincoopRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("FincoopPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val KEY_SAVINGS = "savings_balance"
    private val KEY_LOAN = "loan_balance"
    private val KEY_TRANSACTIONS = "transactions_list"
    private val KEY_USER_NAME = "user_name"
    private val KEY_USER_PHONE = "user_phone"
    private val KEY_USER_PIN = "user_pin"
    private val KEY_USER_IMAGE = "user_image"
    private val KEY_THEME_MODE = "theme_mode"

    fun getSavingsBalance(): Double = prefs.getFloat(KEY_SAVINGS, 25000.0f).toDouble()
    fun getLoanBalance(): Double = prefs.getFloat(KEY_LOAN, 10000.0f).toDouble()

    fun getUserName(default: String): String = prefs.getString(KEY_USER_NAME, default) ?: default
    fun getUserPhone(): String = prefs.getString(KEY_USER_PHONE, "+254 700 123 456") ?: "+254 700 123 456"
    fun getUserImage(): String? = prefs.getString(KEY_USER_IMAGE, null)

    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_THEME_MODE, false)

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_THEME_MODE, enabled).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val storedPin = prefs.getString(KEY_USER_PIN, "1234") ?: "1234"
        return pin == storedPin
    }

    fun updatePin(newPin: String) {
        prefs.edit().putString(KEY_USER_PIN, newPin).apply()
    }

    fun updateProfile(name: String, phone: String) {
        prefs.edit().apply {
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_PHONE, phone)
            apply()
        }
    }

    fun updateProfileImage(uri: String) {
        prefs.edit().putString(KEY_USER_IMAGE, uri).apply()
    }

    fun saveBalances(savings: Double, loan: Double) {
        prefs.edit().apply {
            putFloat(KEY_SAVINGS, savings.toFloat())
            putFloat(KEY_LOAN, loan.toFloat())
            apply()
        }
    }

    fun getTransactions(): List<Transaction> {
        val json = prefs.getString(KEY_TRANSACTIONS, null) ?: return emptyList()
        val type = object : TypeToken<List<Transaction>>() {}.type
        return gson.fromJson(json, type)
    }

    fun addTransaction(transaction: Transaction) {
        val list = getTransactions().toMutableList()
        list.add(0, transaction) // Add to top
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_TRANSACTIONS, json).apply()
    }

    fun deleteTransaction(id: String) {
        val list = getTransactions().toMutableList()
        list.removeAll { it.id == id }
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_TRANSACTIONS, json).apply()
    }

    // --- Mock API Consumption ---
    suspend fun fetchBalancesRemote(): Resource<Pair<Double, Double>> {
        delay(1000)
        return Resource.Success(getSavingsBalance() to getLoanBalance())
    }

    suspend fun loginRemote(email: String, password: String): Resource<String> {
        delay(1500)
        return if (email.contains("@") && password.length >= 4) {
            Resource.Success(email)
        } else {
            Resource.Error("Invalid credentials")
        }
    }

    suspend fun performTransactionRemote(type: TransactionType, amount: Double, desc: String): Resource<Transaction> {
        return try {
            delay(1500) // Simulate network latency
            
            // Mock server-side validation
            if (amount <= 0) throw Exception("Invalid amount")
            
            val transaction = Transaction(type = type, amount = amount, description = desc)
            
            // Local update (Simulating synchronization)
            addTransaction(transaction)
            
            Resource.Success(transaction)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error occurred")
        }
    }
}

// --- ViewModel for Clean Architecture ---
class DashboardViewModel(private val repository: FincoopRepository) : ViewModel() {

    private val _uiState = MutableLiveData<Resource<Pair<Double, Double>>>()
    val uiState: LiveData<Resource<Pair<Double, Double>>> = _uiState

    private val _transactionResult = MutableLiveData<Resource<String>>()
    val transactionResult: LiveData<Resource<String>> = _transactionResult

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = Resource.Loading()
            val result = repository.fetchBalancesRemote()
            _uiState.value = result
        }
    }

    fun processTransaction(type: TransactionType, amount: Double, desc: String) {
        viewModelScope.launch {
            _transactionResult.value = Resource.Loading()
            
            // Check local balance before "sending" to API
            val currentSavings = repository.getSavingsBalance()
            if (type != TransactionType.DEPOSIT && amount > currentSavings) {
                _transactionResult.value = Resource.Error("Insufficient funds")
                return@launch
            }

            val result = repository.performTransactionRemote(type, amount, desc)
            
            if (result is Resource.Success) {
                var savings = repository.getSavingsBalance()
                var loan = repository.getLoanBalance()
                
                when (type) {
                    TransactionType.DEPOSIT -> savings += amount
                    TransactionType.WITHDRAWAL, TransactionType.TRANSFER -> savings -= amount
                    TransactionType.LOAN_REPAYMENT -> {
                        savings -= amount
                        loan -= amount
                    }
                }
                
                repository.saveBalances(savings, loan)
                loadData()
                _transactionResult.value = Resource.Success("Transaction processed successfully")
            } else {
                _transactionResult.value = Resource.Error(result.message ?: "Failed")
            }
        }
    }
}
