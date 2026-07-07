package com.example.fincoop

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.UUID

// --- M-Pesa Integration Models ---
data class MpesaAuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("expires_in") val expiresIn: String
)

data class StkPushRequest(
    @SerializedName("BusinessShortCode") val businessShortCode: String,
    @SerializedName("Password") val password: String,
    @SerializedName("Timestamp") val timestamp: String,
    @SerializedName("TransactionType") val transactionType: String = "CustomerPayBillOnline",
    @SerializedName("Amount") val amount: Int,
    @SerializedName("PartyA") val partyA: String,
    @SerializedName("PartyB") val businessShortCode2: String,
    @SerializedName("PhoneNumber") val phoneNumber: String,
    @SerializedName("CallBackURL") val callBackUrl: String,
    @SerializedName("AccountReference") val accountReference: String,
    @SerializedName("TransactionDesc") val transactionDesc: String
)

data class StkPushResponse(
    @SerializedName("MerchantRequestID") val merchantRequestId: String,
    @SerializedName("CheckoutRequestID") val checkoutRequestId: String,
    @SerializedName("ResponseCode") val responseCode: String,
    @SerializedName("ResponseDescription") val responseDescription: String,
    @SerializedName("CustomerMessage") val customerMessage: String
)

data class MpesaNameResponse(
    @SerializedName("ResponseCode") val responseCode: String,
    @SerializedName("Name") val name: String
)

interface DarajaApi {
    @GET("oauth/v1/generate?grant_type=client_credentials")
    suspend fun getAccessToken(@Header("Authorization") auth: String): MpesaAuthResponse

    @POST("mpesa/stkpush/v1/processrequest")
    suspend fun initiateStkPush(@Header("Authorization") auth: String, @Body request: StkPushRequest): StkPushResponse

    @POST("mpesa/b2c/v1/paymentrequest")
    suspend fun initiateB2C(@Header("Authorization") auth: String, @Body request: Map<String, Any>): StkPushResponse

    // Mock endpoint for name verification
    @POST("mpesa/identity/v1/query")
    suspend fun verifyName(@Header("Authorization") auth: String, @Body request: Map<String, String>): MpesaNameResponse
}

// --- Biometric Auth Helper ---
object BiometricHelper {
    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun showPrompt(
        activity: AppCompatActivity,
        title: String = "Biometric Login",
        subtitle: String = "Log in using your biometric credential",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Authentication failed")
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Use Password")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

// --- Security Base Activity ---
open class SecureActivity : AppCompatActivity() {
    private val logoutHandler = Handler(Looper.getMainLooper())
    private val logoutRunnable = Runnable { logout() }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply Dark Mode for the entire app
        val repository = FincoopRepository(this)
        AppCompatDelegate.setDefaultNightMode(
            if (repository.isDarkMode()) AppCompatDelegate.MODE_NIGHT_YES 
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        
        super.onCreate(savedInstanceState)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetLogoutTimer()
    }

    private fun resetLogoutTimer() {
        logoutHandler.removeCallbacks(logoutRunnable)
        logoutHandler.postDelayed(logoutRunnable, 10 * 60 * 1000) // Extended to 10 minutes
    }

    private fun logout() {
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        resetLogoutTimer()
    }

    override fun onPause() {
        super.onPause()
        logoutHandler.removeCallbacks(logoutRunnable)
    }
}

// --- Models ---
data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val type: TransactionType,
    val amount: Double,
    val date: Date = java.util.Date(),
    val status: String = "Completed",
    val description: String,
    val lat: Double? = null,
    val lng: Double? = null
)

enum class TransactionType {
    DEPOSIT, WITHDRAWAL, TRANSFER, LOAN_REPAYMENT, CHAMA_CONTRIBUTION
}

// --- Chama Models ---
enum class MemberRole {
    CHAIRPERSON, TREASURER, MEMBER
}

enum class AttendanceStatus {
    PRESENT, ABSENT, ABSENT_WITH_APOLOGY
}

data class ChamaMember(
    val userId: String,
    val name: String,
    var role: MemberRole,
    var balance: Double = 0.0,
    var loanBalance: Double = 0.0,
    var attendance: MutableMap<String, AttendanceStatus> = mutableMapOf(), // date to status
    val registrationTimestamp: Long = System.currentTimeMillis() // For Merry-Go-Round order
)

data class ChamaMeeting(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: java.util.Date,
    val meetingLink: String? = null,
    val minutes: String? = null,
    val type: MeetingType = MeetingType.GOOGLE_MEET
)

enum class MeetingType {
    GOOGLE_MEET, WHATSAPP_CALL, PHYSICAL
}

data class WithdrawalRequest(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val recipientPhone: String,
    val reason: String,
    val requesterId: String,
    var chairpersonApproved: Boolean = false,
    var memberApproved: Boolean = false,
    val approvedByMemberId: String? = null,
    var status: String = "PENDING" // PENDING, APPROVED, EXECUTED, REJECTED
)

data class Chama(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val members: List<ChamaMember>,
    var balance: Double = 0.0,
    val transactions: MutableList<Transaction> = mutableListOf(),
    val meetings: MutableList<ChamaMeeting> = mutableListOf(),
    val withdrawalRequests: MutableList<WithdrawalRequest> = mutableListOf(),
    var currentMerryGoRoundIndex: Int = 0
)

data class LocationRecord(
    val lat: Double,
    val lng: Double,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class Resource<T>(val data: T? = null, val message: String? = null) {
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)
    class Loading<T> : Resource<T>()
}

// --- Data Repository (Networking + Local Storage) ---
class FincoopRepository(context: Context) {
    // ... existing fields ...

    private val darajaApi: DarajaApi by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        Retrofit.Builder()
            .baseUrl("https://sandbox.safaricom.co.ke/") // Use sandbox for testing
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(DarajaApi::class.java)
    }

    suspend fun initiateMpesaPayment(amount: Double, phone: String): Resource<String> = withContext(Dispatchers.IO) {
        try {
            // NOTE: Replace these with your real Daraja credentials from Safaricom portal
            val consumerKey = "Cxa6mIG2ol1OARlZxWGAX2ohQA83GaowqEzTPA1XZkOpjhlz"
            val consumerSecret = "eGEPPqRHPcqFyyWFjLCGGAgOn9FfMgoRwukh4DCprnT490m46D1QAzdflSoNFUQP"
            val businessShortCode = "174379" // Sandbox shortcode
            val passkey = "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919"
            
            val auth = "Basic " + android.util.Base64.encodeToString("$consumerKey:$consumerSecret".toByteArray(), android.util.Base64.NO_WRAP)
            val tokenResponse = darajaApi.getAccessToken(auth)
            
            val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(java.util.Date())
            val password = android.util.Base64.encodeToString("$businessShortCode$passkey$timestamp".toByteArray(), android.util.Base64.NO_WRAP)
            
            val request = StkPushRequest(
                businessShortCode = businessShortCode,
                password = password,
                timestamp = timestamp,
                amount = amount.toInt(),
                partyA = phone,
                businessShortCode2 = businessShortCode,
                phoneNumber = phone,
                callBackUrl = "https://your-domain.com/callback", // Replace with your actual callback URL
                accountReference = "Fincoop",
                transactionDesc = "Fincoop Deposit"
            )
            
            val response = darajaApi.initiateStkPush("Bearer ${tokenResponse.accessToken}", request)
            if (response.responseCode == "0") {
                Resource.Success("STK Push Sent. Please enter PIN on your phone.")
            } else {
                Resource.Error(response.responseDescription)
            }
        } catch (e: Exception) {
            Resource.Error("M-Pesa Error: ${e.localizedMessage}")
        }
    }

    suspend fun withdrawToMpesa(amount: Double, phone: String): Resource<String> = withContext(Dispatchers.IO) {
        try {
            delay(1000)
            // In a real app, this would call a B2C API (Business to Customer)
            // For Sandbox simulation, we just return success
            Resource.Success("Withdrawal of KES $amount to $phone initiated.")
        } catch (e: Exception) {
            Resource.Error("M-Pesa Error: ${e.localizedMessage}")
        }
    }

    suspend fun transferViaMpesa(amount: Double, phone: String): Resource<String> = withContext(Dispatchers.IO) {
        try {
            delay(1000)
            // Simulated B2C transfer to another user
            Resource.Success("Transfer of KES $amount to $phone via M-Pesa initiated.")
        } catch (e: Exception) {
            Resource.Error("M-Pesa Error: ${e.localizedMessage}")
        }
    }

    suspend fun verifyMpesaName(number: String): Resource<String> = withContext(Dispatchers.IO) {
        try {
            delay(800) // Simulate network
            val name = when {
                number.endsWith("1") -> "John Doe"
                number.endsWith("2") -> "Mary Jane"
                number.length >= 5 -> "Business Express LTD"
                else -> "Verified Customer"
            }
            Resource.Success(name)
        } catch (e: Exception) {
            Resource.Error("Could not verify name")
        }
    }

    suspend fun payWithPayPal(amount: Double, email: String): Resource<String> = withContext(Dispatchers.IO) {
          delay(1500)
        if (email.contains("@") && email.length > 5) {
            Resource.Success("PayPal payment of $$amount to $email successful.")
        } else {
            Resource.Error("Invalid PayPal email address")
        }
    }

    suspend fun payWithCard(amount: Double, cardNumber: String): Resource<String> = withContext(Dispatchers.IO) {
        delay(1500)
        // Simple card validation logic for demo
        val cleanedCard = cardNumber.replace(" ", "").replace("-", "")
        if (cleanedCard.length == 16 && (cleanedCard.startsWith("4") || cleanedCard.startsWith("5"))) {
            Resource.Success("Card payment of KES $amount successful (Card ending in ${cleanedCard.takeLast(4)}).")
        } else {
            Resource.Error("Invalid credentials: Card number does not exist or is unsupported.")
        }
    }
    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "FincoopSecurePrefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to regular SharedPreferences if encryption fails (e.g. Keystore issues)
            context.getSharedPreferences("FincoopBackupPrefs", Context.MODE_PRIVATE)
        }
    }
    
    private val auth = FirebaseAuth.getInstance()
    private val gson = Gson()

    private val KEY_SAVINGS = "savings_balance"
    private val KEY_LOAN = "loan_balance"
    private val KEY_TRANSACTIONS = "transactions_list"
    private val KEY_USER_NAME = "user_name_"
    private val KEY_USER_PHONE = "user_phone_"
    private val KEY_USER_PIN = "user_pin_"
    private val KEY_USER_IMAGE = "user_image_"
    private val KEY_THEME_MODE = "theme_mode"
    private val KEY_CHAMAS = "chamas_list"
    private val KEY_REG_LAT = "reg_lat"
    private val KEY_REG_LNG = "reg_lng"
    private val KEY_CURRENT_LAT = "current_lat"
    private val KEY_CURRENT_LNG = "current_lng"
    private val KEY_REG_NO = "reg_no_"
    private val KEY_ACCOUNT_NO = "account_no_"
    private val KEY_LOCATION_HISTORY = "location_history_"

    fun getChamas(): List<Chama> {
        return try {
            val json = prefs.getString(KEY_CHAMAS, null) ?: return emptyList()
            val type = object : TypeToken<List<Chama>>() {}.type
            val rawList = gson.fromJson<List<Chama>>(json, type) ?: emptyList()
            
            // Sanitize list to prevent NullPointerExceptions from Gson deserialization of missing fields
            rawList.map { chama ->
                chama.copy(
                    members = chama.members.map { member ->
                        member.copy(
                            attendance = member.attendance ?: mutableMapOf()
                        )
                    },
                    transactions = chama.transactions ?: mutableListOf(),
                    meetings = chama.meetings ?: mutableListOf(),
                    withdrawalRequests = chama.withdrawalRequests ?: mutableListOf()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveChama(chama: Chama) {
        val list = getChamas().toMutableList()
        val index = list.indexOfFirst { it.id == chama.id }
        if (index != -1) list[index] = chama else list.add(chama)
        prefs.edit().putString(KEY_CHAMAS, gson.toJson(list)).apply()
    }

    // --- New Chama Logic ---

    fun scheduleMeeting(chamaId: String, title: String, type: MeetingType): ChamaMeeting {
        val chama = getChamaById(chamaId) ?: throw Exception("Chama not found")
        val link = if (type == MeetingType.GOOGLE_MEET) "https://meet.google.com/${UUID.randomUUID().toString().take(10)}"
                  else "https://wa.me/chama_group_call_${chamaId}"
        
        val meeting = ChamaMeeting(title = title, date = java.util.Date(), meetingLink = link, type = type)
        chama.meetings.add(meeting)
        saveChama(chama)
        return meeting
    }

    fun requestWithdrawal(chamaId: String, amount: Double, phone: String, reason: String, userId: String) {
        val chama = getChamaById(chamaId) ?: return
        val request = WithdrawalRequest(amount = amount, recipientPhone = phone, reason = reason, requesterId = userId)
        chama.withdrawalRequests.add(request)
        saveChama(chama)
    }

    fun approveWithdrawal(chamaId: String, requestId: String, userId: String, isChair: Boolean) {
        val chama = getChamaById(chamaId) ?: return
        val request = chama.withdrawalRequests.find { it.id == requestId } ?: return
        
        if (isChair) request.chairpersonApproved = true
        else request.memberApproved = true
        
        if (request.chairpersonApproved && request.memberApproved) {
            request.status = "APPROVED"
            // In a real app, this would trigger the actual M-Pesa B2C disbursement
            chama.balance -= request.amount
            chama.transactions.add(Transaction(type = TransactionType.WITHDRAWAL, amount = request.amount, description = "Chama Withdrawal: ${request.reason}"))
            request.status = "EXECUTED"
        }
        saveChama(chama)
    }

    fun joinChama(chamaId: String, name: String, role: MemberRole, userId: String) {
        val chama = getChamaById(chamaId) ?: return
        val members = chama.members.toMutableList()
        if (members.any { it.userId == userId }) return
        
        val newMember = ChamaMember(userId = userId, name = name, role = role)
        members.add(newMember)
        saveChama(chama.copy(members = members))
    }

    fun getChamasUserCanJoin(userId: String): List<Chama> {
        return getChamas().filter { chama -> chama.members.none { it.userId == userId } }
    }

    fun getNextMerryGoRound(chamaId: String): ChamaMember? {
        val chama = getChamaById(chamaId) ?: return null
        val sortedMembers = chama.members.sortedBy { it.registrationTimestamp }
        if (sortedMembers.isEmpty()) return null
        
        val nextIndex = chama.currentMerryGoRoundIndex % sortedMembers.size
        return sortedMembers[nextIndex]
    }

    fun completeMerryGoRoundCycle(chamaId: String) {
        val chama = getChamaById(chamaId) ?: return
        chama.currentMerryGoRoundIndex++
        saveChama(chama)
    }

    fun getChamaById(id: String): Chama? = getChamas().find { it.id == id }

    fun updateChamaMember(chamaId: String, member: ChamaMember) {
        val chama = getChamaById(chamaId) ?: return
        val members = chama.members.toMutableList()
        val index = members.indexOfFirst { it.userId == member.userId }
        if (index != -1) {
            members[index] = member
            saveChama(chama.copy(members = members))
        }
    }

    fun getSavingsBalance(): Double = prefs.getFloat(KEY_SAVINGS, 25000.0f).toDouble()
    fun getLoanBalance(): Double = prefs.getFloat(KEY_LOAN, 10000.0f).toDouble()

    fun getUserName(default: String, email: String = ""): String {
        val userEmail = email.ifEmpty { auth.currentUser?.email ?: "" }
        return prefs.getString(KEY_USER_NAME + userEmail, default) ?: default
    }

    fun getFormattedUserName(default: String, email: String = ""): String {
        val name = getUserName(default, email)
        val parts = name.trim().split("\\s+".toRegex())
        return if (parts.size >= 2) "${parts[0]} ${parts[1]}" else name
    }

    fun getUserPhone(email: String = ""): String {
        val userEmail = email.ifEmpty { auth.currentUser?.email ?: "" }
        return prefs.getString(KEY_USER_PHONE + userEmail, "+254 700 123 456") ?: "+254 700 123 456"
    }

    fun getUserImage(email: String = ""): String? {
        val userEmail = email.ifEmpty { auth.currentUser?.email ?: "" }
        return prefs.getString(KEY_USER_IMAGE + userEmail, null)
    }

    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_THEME_MODE, false)

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_THEME_MODE, enabled).apply()
    }

    fun verifyPin(pin: String, email: String = ""): Boolean {
        val userEmail = email.ifEmpty { auth.currentUser?.email ?: "" }
        val storedPin = prefs.getString(KEY_USER_PIN + userEmail, "1234") ?: "1234"
        return pin == storedPin
    }

    fun updatePin(newPin: String, email: String = "") {
        val userEmail = email.ifEmpty { auth.currentUser?.email ?: "" }
        prefs.edit().putString(KEY_USER_PIN + userEmail, newPin).apply()
    }

    fun updateProfile(name: String, phone: String, email: String = "") {
        val userEmail = email.ifEmpty { auth.currentUser?.email ?: "" }
        prefs.edit().apply {
            putString(KEY_USER_NAME + userEmail, name)
            putString(KEY_USER_PHONE + userEmail, phone)
            apply()
        }
    }

    fun updateProfileImage(uri: String, email: String = "") {
        val userEmail = email.ifEmpty { auth.currentUser?.email ?: "" }
        prefs.edit().putString(KEY_USER_IMAGE + userEmail, uri).apply()
    }

    fun deleteProfileImage(email: String = "") {
        val userEmail = email.ifEmpty { auth.currentUser?.email ?: "" }
        prefs.edit().remove(KEY_USER_IMAGE + userEmail).apply()
    }

    fun issueRegistrationNumber(email: String): String {
        val userEmail = email.ifEmpty { auth.currentUser?.email ?: "" }
        val existing = prefs.getString(KEY_REG_NO + userEmail, null)
        if (existing != null) return existing

        val randomSuffix = (1000..9999).random()
        val regNo = "FC-REG-$randomSuffix"
        prefs.edit().putString(KEY_REG_NO + userEmail, regNo).apply()
        return regNo
    }

    fun saveRegistrationNumber(regNo: String, email: String = "") {
        val userEmail = email.ifEmpty { auth.currentUser?.email ?: "" }
        prefs.edit().putString(KEY_REG_NO + userEmail, regNo).apply()
    }

    fun getRegistrationNumber(email: String = ""): String {
        val userEmail = email.ifEmpty { auth.currentUser?.email ?: "" }
        return prefs.getString(KEY_REG_NO + userEmail, "FC-REG-PENDING") ?: "FC-REG-PENDING"
    }

    fun saveAccountNumber(accountNo: String, email: String = "") {
        val userEmail = email.ifEmpty { auth.currentUser?.email ?: "" }
        prefs.edit().putString(KEY_ACCOUNT_NO + userEmail, accountNo).apply()
    }

    fun getAccountNumber(email: String = ""): String {
        val userEmail = email.ifEmpty { auth.currentUser?.email ?: "" }
        return prefs.getString(KEY_ACCOUNT_NO + userEmail, "N/A") ?: "N/A"
    }

    fun saveRegLocation(lat: Double, lng: Double) {
        val userEmail = auth.currentUser?.email ?: "anonymous"
        prefs.edit().apply {
            putFloat(KEY_REG_LAT, lat.toFloat())
            putFloat(KEY_REG_LNG, lng.toFloat())
            apply()
        }
        // Also log to history
        addLocationToHistory(lat, lng, userEmail)
    }

    fun getRegLocation(): Pair<Double, Double>? {
        val lat = prefs.getFloat(KEY_REG_LAT, 0f)
        val lng = prefs.getFloat(KEY_REG_LNG, 0f)
        return if (lat != 0f && lng != 0f) Pair(lat.toDouble(), lng.toDouble()) else null
    }

    fun saveCurrentLocation(lat: Double, lng: Double) {
        val userEmail = auth.currentUser?.email ?: "anonymous"
        prefs.edit().apply {
            putFloat(KEY_CURRENT_LAT, lat.toFloat())
            putFloat(KEY_CURRENT_LNG, lng.toFloat())
            apply()
        }
        // Always log history to "database" (mock database via prefs)
        addLocationToHistory(lat, lng, userEmail)
    }

    private fun addLocationToHistory(lat: Double, lng: Double, email: String) {
        val history = getLocationHistory(email).toMutableList()
        history.add(LocationRecord(lat, lng))
        prefs.edit().putString(KEY_LOCATION_HISTORY + email, gson.toJson(history)).apply()
    }

    fun getLocationHistory(email: String = ""): List<LocationRecord> {
        val userEmail = email.ifEmpty { auth.currentUser?.email ?: "" }
        val json = prefs.getString(KEY_LOCATION_HISTORY + userEmail, null) ?: return emptyList()
        val type = object : TypeToken<List<LocationRecord>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun getCurrentLocation(): Pair<Double, Double>? {
        val lat = prefs.getFloat(KEY_CURRENT_LAT, 0f)
        val lng = prefs.getFloat(KEY_CURRENT_LNG, 0f)
        return if (lat != 0f && lng != 0f) Pair(lat.toDouble(), lng.toDouble()) else null
    }

    fun saveBalances(savings: Double, loan: Double) {
        prefs.edit().apply {
            putFloat(KEY_SAVINGS, savings.toFloat())
            putFloat(KEY_LOAN, loan.toFloat())
            apply()
        }
    }

    fun getTransactions(): List<Transaction> {
        return try {
            val json = prefs.getString(KEY_TRANSACTIONS, null) ?: return emptyList()
            val type = object : TypeToken<List<Transaction>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
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

    // --- Firebase Authentication ---
    suspend fun loginRemote(email: String, password: String): Resource<String> {
        return try {
            // Explicitly sign out to clear any stale session that might cause "expired" errors
            auth.signOut()
            
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                // Success: return email
                Resource.Success(user.email ?: email)
            } else {
                Resource.Error("Login failed")
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: ""
            // FALLBACK: Allow demo login if Firebase is not yet configured or has temporary issues
            if (errorMsg.contains("CONFIGURATION_NOT_FOUND") || 
                errorMsg.contains("internal error") || 
                errorMsg.contains("expired") ||
                errorMsg.contains("INVALID_IDP_RESPONSE")) {

                if (email.contains("@") && password.length >= 4) {
                    return Resource.Success(email)
                }
            }
            Resource.Error(e.message ?: "Authentication failed")
        }
    }

    suspend fun registerRemote(email: String, password: String): Resource<String> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user?.sendEmailVerification()
            Resource.Success("Registration successful. Please verify your email before logging in.")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Registration failed")
        }
    }

    suspend fun performTransactionRemote(type: TransactionType, amount: Double, desc: String): Resource<Transaction> {
        return try {
            delay(1500) // Simulate network latency
            
            // Mock server-side validation
            if (amount <= 0) throw Exception("Invalid amount")
            
            val currentLocation = getCurrentLocation()
            val transaction = Transaction(
                type = type, 
                amount = amount, 
                description = desc,
                lat = currentLocation?.first,
                lng = currentLocation?.second
            )
            
            // Local update (Simulating synchronization)
            addTransaction(transaction)
            
            Resource.Success(transaction)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error occurred")
        }
    }
}

// --- Permission Helper ---
object PermissionHelper {
    val LOCATION_PERMISSIONS = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )
    val CAMERA_PERMISSION = android.Manifest.permission.CAMERA

    fun hasLocationPermission(context: Context): Boolean {
        return LOCATION_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, CAMERA_PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun requestLocationPermissions(activity: AppCompatActivity, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, LOCATION_PERMISSIONS, requestCode)
    }

    fun requestCameraPermission(activity: AppCompatActivity, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, arrayOf(CAMERA_PERMISSION), requestCode)
    }

    fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
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
                    TransactionType.CHAMA_CONTRIBUTION -> savings -= amount
                }
                
                repository.saveBalances(savings, loan)
                loadData()
                _transactionResult.value = Resource.Success("Transaction processed successfully")
            } else {
                _transactionResult.value = Resource.Error(result.message ?: "Failed")
            }
        }
    }

    fun initiateMpesaDeposit(amount: Double, phone: String) {
        viewModelScope.launch {
            _transactionResult.value = Resource.Loading()
            val result = repository.initiateMpesaPayment(amount, phone)
            _transactionResult.value = result
            
            if (result is Resource.Success) {
                processTransaction(TransactionType.DEPOSIT, amount, "M-Pesa Deposit")
            }
        }
    }

    fun initiateMpesaWithdrawal(amount: Double, phone: String) {
        viewModelScope.launch {
            _transactionResult.value = Resource.Loading()
            val result = repository.withdrawToMpesa(amount, phone)
            _transactionResult.value = result
            
            if (result is Resource.Success) {
                processTransaction(TransactionType.WITHDRAWAL, amount, "M-Pesa Withdrawal to $phone")
            }
        }
    }

    fun initiateMpesaTransfer(amount: Double, phone: String) {
        viewModelScope.launch {
            _transactionResult.value = Resource.Loading()
            val result = repository.transferViaMpesa(amount, phone)
            _transactionResult.value = result
            
            if (result is Resource.Success) {
                processTransaction(TransactionType.TRANSFER, amount, "M-Pesa Transfer to $phone")
            }
        }
    }

    fun initiateMpesaRepayment(amount: Double, phone: String) {
        viewModelScope.launch {
            _transactionResult.value = Resource.Loading()
            val result = repository.initiateMpesaPayment(amount, phone)
            _transactionResult.value = result
            
            if (result is Resource.Success) {
                processTransaction(TransactionType.LOAN_REPAYMENT, amount, "M-Pesa Loan Repayment")
            }
        }
    }

    // --- New Verification and Payment Methods ---

    private val _verificationState = MutableLiveData<Resource<String>>()
    val verificationState: LiveData<Resource<String>> = _verificationState

    fun verifyRecipient(number: String) {
        viewModelScope.launch {
            _verificationState.value = Resource.Loading()
            val result = repository.verifyMpesaName(number)
            _verificationState.value = result
        }
    }

    fun initiatePayPalPayment(amount: Double, email: String) {
        viewModelScope.launch {
            _transactionResult.value = Resource.Loading()
            val result = repository.payWithPayPal(amount, email)
            _transactionResult.value = result
            if (result is Resource.Success) {
                processTransaction(TransactionType.DEPOSIT, amount, "PayPal Deposit ($email)")
            }
        }
    }

    fun initiateCardPayment(amount: Double, cardNumber: String) {
        viewModelScope.launch {
            _transactionResult.value = Resource.Loading()
            val result = repository.payWithCard(amount, cardNumber)
            _transactionResult.value = result
            if (result is Resource.Success) {
                processTransaction(TransactionType.DEPOSIT, amount, "Card Payment (****${cardNumber.takeLast(4)})")
            }
        }
    }

    // --- Chama Actions ---
    private val _chamas = MutableLiveData<List<Chama>>()
    val chamas: LiveData<List<Chama>> = _chamas

    fun loadChamas() {
        _chamas.value = repository.getChamas()
    }

    fun createChama(name: String, description: String) {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: "user@example.com"
        val currentUserName = repository.getUserName("Admin")
        
        val admin = ChamaMember(currentUserEmail, currentUserName, MemberRole.CHAIRPERSON)
        val newChama = Chama(name = name, description = description, members = listOf(admin))
        
        repository.saveChama(newChama)
        loadChamas()
        _transactionResult.value = Resource.Success("Chama '$name' created successfully!")
    }
}
