package com.example.fincoop

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardActivity : SecureActivity() {

    private val repository by lazy { FincoopRepository(this) }
    private val viewModel: DashboardViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DashboardViewModel(repository) as T
            }
        }
    }

    private lateinit var transactionAdapter: TransactionAdapter
    private lateinit var gestureDetector: GestureDetector
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val LOCATION_REQ_CODE = 1001
    private val CAMERA_REQ_CODE = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupUI()
        observeViewModel()
        checkPermissions()
    }

    private fun checkPermissions() {
        if (!PermissionHelper.hasLocationPermission(this)) {
            PermissionHelper.requestLocationPermissions(this, LOCATION_REQ_CODE)
        } else {
            if (!PermissionHelper.isLocationEnabled(this)) {
                showGPSDisabledDialog()
            } else {
                startLocationUpdates()
            }
        }
    }

    private fun showGPSDisabledDialog() {
        AlertDialog.Builder(this)
            .setTitle("GPS Disabled")
            .setMessage("Your GPS seems to be disabled. Fincoop requires active location services for security. Please turn on GPS to continue.")
            .setPositiveButton("Turn On") { _, _ ->
                startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Exit App") { _, _ ->
                finishAffinity()
            }
            .setCancelable(false)
            .show()
    }

    private fun startLocationUpdates() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    repository.saveCurrentLocation(it.latitude, it.longitude)
                    updateLocationUI(it.latitude, it.longitude)
                }
            }
        } catch (e: SecurityException) {
            showSnackbar("Location permission missing", true)
        }
    }

    private fun updateLocationUI(lat: Double, lng: Double) {
        // Removed account number display. Showing location in snackbar once.
        showSnackbar("Location updated: ${String.format("%.4f", lat)}, ${String.format("%.4f", lng)}", false)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_REQ_CODE -> {
                if (PermissionHelper.hasLocationPermission(this)) {
                    startLocationUpdates()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Location Required")
                        .setMessage("Fincoop requires location access for security and regulatory compliance. Please allow location access to use all features.")
                        .setPositiveButton("Grant Permission") { _, _ ->
                            PermissionHelper.requestLocationPermissions(this, LOCATION_REQ_CODE)
                        }
                        .setNegativeButton("Exit App") { _, _ ->
                            finishAffinity()
                        }
                        .setCancelable(false)
                        .show()
                }
            }
            CAMERA_REQ_CODE -> {
                if (!PermissionHelper.hasCameraPermission(this)) {
                    showSnackbar("Camera permission denied.", true)
                }
            }
        }
    }

    private fun setupUI() {
        setupRecyclerView()
        setupQuickActions()
        setupBottomNavigation()
        setupWelcomeMessage()

        findViewById<TextView>(R.id.txtSeeAll).setOnClickListener {
            startActivity(Intent(this, TransactionHistoryActivity::class.java))
        }
    }

    private fun setupWelcomeMessage() {
        val email = intent.getStringExtra("USER_EMAIL") ?: "User"
        val defaultName = if (email.contains("@")) email.substringBefore("@").replaceFirstChar { it.uppercase() } else email
        val name = repository.getFormattedUserName(defaultName, email)
        findViewById<TextView>(R.id.txtWelcome).text = "Welcome, $name"
        
        // Set profile image if available
        val imageUri = repository.getUserImage(email)
        val profileImage = findViewById<ImageView>(R.id.profileImage)
        if (profileImage != null) {
            if (imageUri != null) {
                try {
                    profileImage.setImageURI(android.net.Uri.parse(imageUri))
                } catch (e: Exception) {
                    profileImage.setImageResource(R.mipmap.ic_launcher_round)
                }
            } else {
                profileImage.setImageResource(R.mipmap.ic_launcher_round)
            }

            // --- GESTURE EXAMPLES START ---
            
            // 1. Double Tap (using GestureDetector)
            gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    showSnackbar("Double Tapped Profile Image! Opening Gallery...", false)
                    return true
                }
            })

            profileImage.setOnTouchListener { v, event ->
                gestureDetector.onTouchEvent(event)
                v.performClick()
                true
            }

            // 2. onLongPress (Long Click)
            profileImage.setOnLongClickListener {
                showSnackbar("Long Pressed Profile Image! Change photo?", false)
                true // Consumed
            }

            // 3. onTap (Standard Click)
            profileImage.setOnClickListener {
                showSnackbar("Tapped Profile Image!", false)
            }
            
            // --- GESTURE EXAMPLES END ---
        }
    }

    override fun onResume() {
        super.onResume()
        setupWelcomeMessage() // Refresh name if updated in Profile
        viewModel.loadData()
        checkPermissions() // Always ask while using the app
    }

    private fun setupRecyclerView() {
        val rv = findViewById<RecyclerView>(R.id.rvTransactions)
        transactionAdapter = TransactionAdapter(onLongClick = { transaction ->
            // Existing long click
        }, onMapClick = { transaction ->
            if (transaction.lat != null && transaction.lng != null) {
                val mapIntent = Intent(this, MapActivity::class.java).apply {
                    putExtra("LAT", transaction.lat)
                    putExtra("LNG", transaction.lng)
                    putExtra("TITLE", transaction.description)
                }
                startActivity(mapIntent)
            } else {
                showSnackbar("No location recorded for this transaction", true)
            }
        })
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = transactionAdapter
        
        // Load initial transactions
        transactionAdapter.submitList(repository.getTransactions().take(5))
    }

    private fun observeViewModel() {
        val loadingIndicator = findViewById<LinearProgressIndicator>(R.id.loadingIndicator)
        val txtSavings = findViewById<TextView>(R.id.txtSavingsAmount)
        val txtLoan = findViewById<TextView>(R.id.txtLoanAmount)

        viewModel.uiState.observe(this) { resource ->
            when (resource) {
                is Resource.Success -> {
                    val (savings, loan) = resource.data!!
                    txtSavings.text = "KES ${String.format("%,.0f", savings)}"
                    txtLoan.text = "KES ${String.format("%,.0f", loan)}"
                    // Update transaction list too
                    transactionAdapter.submitList(repository.getTransactions().take(5))
                }
                else -> {}
            }
        }

        viewModel.transactionResult.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> loadingIndicator.visibility = View.VISIBLE
                is Resource.Success -> {
                    loadingIndicator.visibility = View.GONE
                    showSnackbar(resource.data ?: "Success", false)
                }
                is Resource.Error -> {
                    loadingIndicator.visibility = View.GONE
                    showSnackbar(resource.message ?: "Error", true)
                }
            }
        }
    }

    private fun setupQuickActions() {
        findViewById<View>(R.id.actionDeposit).setOnClickListener {
            showDepositDialog()
        }

        findViewById<View>(R.id.actionWithdraw).setOnClickListener {
            showWithdrawDialog()
        }

        findViewById<View>(R.id.actionTransfer).setOnClickListener {
            showTransferDialog()
        }

        findViewById<View>(R.id.actionRepay).setOnClickListener {
            showRepayLoanDialog()
        }

        findViewById<View>(R.id.actionBuildChama).setOnClickListener {
            showBuildChamaDialog()
        }

        findViewById<View>(R.id.actionChamaGroup).setOnClickListener {
            showChamaListDialog()
        }

        findViewById<View>(R.id.actionLoan).setOnClickListener {
            startActivity(Intent(this, LoanApplicationActivity::class.java))
        }

        findViewById<View>(R.id.actionJoinChama).setOnClickListener {
            showJoinChamaDialog()
        }
    }

    private fun confirmAndAuthenticate(title: String, summary: String, onAuthenticated: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Confirm $title")
            .setMessage("$summary\n\nDo you want to proceed?")
            .setPositiveButton("Confirm") { _, _ ->
                showPinDialog(onAuthenticated)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPinDialog(onAuthenticated: () -> Unit) {
        val email = intent.getStringExtra("USER_EMAIL") ?: ""
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "4-digit PIN"
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 20, 80, 0)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("Enter Transaction PIN")
            .setView(container)
            .setPositiveButton("Verify") { _, _ ->
                val pin = input.text.toString()
                if (repository.verifyPin(pin, email)) {
                    onAuthenticated()
                } else {
                    showSnackbar("The PIN entered was incorrect", true)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDepositDialog() {
        val amountInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Amount"
        }
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 0)
            addView(amountInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Deposit Funds")
            .setView(container)
            .setItems(arrayOf("M-Pesa", "PayPal", "Credit Card", "Manual Deposit")) { _, which ->
                val amount = amountInput.text.toString().toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    showSnackbar("Please enter a valid amount first", true)
                    return@setItems
                }

                when (which) {
                    0 -> showMpesaDialog(amount, "Deposit")
                    1 -> showPayPalDialog(amount)
                    2 -> showCardDialog(amount)
                    3 -> confirmAndAuthenticate("Deposit", "Manual deposit of KES $amount") {
                        viewModel.processTransaction(TransactionType.DEPOSIT, amount, "Manual Deposit")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMpesaDialog(amount: Double, actionType: String) {
        val email = intent.getStringExtra("USER_EMAIL") ?: ""
        val phoneInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            hint = "Business/Phone Number"
            setText(repository.getUserPhone(email).replace("+", "").replace(" ", ""))
        }
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 20)
            addView(phoneInput)
        }

        AlertDialog.Builder(this)
            .setTitle("M-Pesa $actionType")
            .setView(container)
            .setPositiveButton("Verify & Pay") { _, _ ->
                val number = phoneInput.text.toString().trim()
                if (number.isNotEmpty()) {
                    verifyAndConfirmMpesa(amount, number, actionType)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun verifyAndConfirmMpesa(amount: Double, number: String, actionType: String) {
        val progress = AlertDialog.Builder(this).setMessage("Verifying recipient...").show()
        viewModel.verifyRecipient(number)
        
        viewModel.verificationState.observe(this) { resource ->
            if (resource !is Resource.Loading) {
                progress.dismiss()
                viewModel.verificationState.removeObservers(this)
                
                if (resource is Resource.Success) {
                    val verifiedName = resource.data!!
                    AlertDialog.Builder(this)
                        .setTitle("Confirm Transaction")
                        .setMessage("Pay KES $amount to:\n\n$verifiedName ($number)\n\nIs this correct?")
                        .setPositiveButton("Yes, Pay") { _, _ ->
                            when (actionType) {
                                "Deposit" -> viewModel.initiateMpesaDeposit(amount, number)
                                "Withdraw" -> viewModel.initiateMpesaWithdrawal(amount, number)
                                "Transfer" -> viewModel.initiateMpesaTransfer(amount, number)
                                "Repayment" -> viewModel.initiateMpesaRepayment(amount, number)
                            }
                        }
                        .setNegativeButton("No", null)
                        .show()
                } else {
                    showSnackbar("Verification failed. Please check the number.", true)
                }
            }
        }
    }

    private fun showPayPalDialog(amount: Double) {
        val emailInput = EditText(this).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            hint = "PayPal Email"
        }
        AlertDialog.Builder(this)
            .setTitle("PayPal Payment")
            .setView(emailInput)
            .setPositiveButton("Pay") { _, _ ->
                val email = emailInput.text.toString().trim()
                if (email.isNotEmpty()) {
                    viewModel.initiatePayPalPayment(amount, email)
                } else {
                    showSnackbar("Please enter an email", true)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCardDialog(amount: Double) {
        val cardInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "16-digit Card Number"
        }
        AlertDialog.Builder(this)
            .setTitle("Credit Card Payment")
            .setView(cardInput)
            .setPositiveButton("Pay") { _, _ ->
                val card = cardInput.text.toString().trim()
                if (card.isNotEmpty()) {
                    viewModel.initiateCardPayment(amount, card)
                } else {
                    showSnackbar("Please enter a card number", true)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showWithdrawDialog() {
        val amountInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Amount"
        }
        AlertDialog.Builder(this)
            .setTitle("Withdraw Funds")
            .setView(amountInput)
            .setItems(arrayOf("M-Pesa", "Manual Withdraw")) { _, which ->
                val amount = amountInput.text.toString().toDoubleOrNull() ?: return@setItems
                if (which == 0) showMpesaDialog(amount, "Withdraw")
                else confirmAndAuthenticate("Withdrawal", "Manual withdraw of KES $amount") {
                    viewModel.processTransaction(TransactionType.WITHDRAWAL, amount, "Manual Withdrawal")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTransferDialog() {
        val amountInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Amount"
        }
        AlertDialog.Builder(this)
            .setTitle("Transfer Funds")
            .setView(amountInput)
            .setItems(arrayOf("M-Pesa", "Internal Transfer")) { _, which ->
                val amount = amountInput.text.toString().toDoubleOrNull() ?: return@setItems
                if (which == 0) showMpesaDialog(amount, "Transfer")
                else {
                    // Internal transfer logic
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRepayLoanDialog() {
        val amountInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Amount"
        }
        AlertDialog.Builder(this)
            .setTitle("Repay Loan")
            .setView(amountInput)
            .setItems(arrayOf("M-Pesa", "Pay from Savings")) { _, which ->
                val amount = amountInput.text.toString().toDoubleOrNull() ?: return@setItems
                if (which == 0) showMpesaDialog(amount, "Repayment")
                else confirmAndAuthenticate("Repayment", "Repay KES $amount from savings") {
                    viewModel.processTransaction(TransactionType.LOAN_REPAYMENT, amount, "Loan Repayment from Savings")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBuildChamaDialog() {
        val nameInput = EditText(this).apply { hint = "Chama Name" }
        val descInput = EditText(this).apply { hint = "Purpose/Description" }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 0)
            addView(nameInput)
            addView(descInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Build New Chama")
            .setView(container)
            .setPositiveButton("Create") { _, _ ->
                val name = nameInput.text.toString().trim()
                val desc = descInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.createChama(name, desc)
                } else {
                    showSnackbar("Chama name is required", true)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showJoinChamaDialog() {
        val userEmail = intent.getStringExtra("USER_EMAIL") ?: "User"
        val availableChamas = repository.getChamasUserCanJoin(userEmail)
        
        if (availableChamas.isEmpty()) {
            showSnackbar("No available Chamas to join at the moment.", true)
            return
        }

        val names = availableChamas.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Chama to Join")
            .setItems(names) { _, which ->
                showJoinRoleDialog(availableChamas[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showJoinRoleDialog(chama: Chama) {
        val userEmail = intent.getStringExtra("USER_EMAIL") ?: "User"
        val nameInput = EditText(this).apply { 
            hint = "Your Name"
            setText(repository.getUserName("")) 
        }
        
        val roles = MemberRole.values().map { it.name }.toTypedArray()
        var selectedRoleIndex = 2 // Default to MEMBER

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 0)
            addView(nameInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Join ${chama.name}")
            .setView(container)
            .setSingleChoiceItems(roles, selectedRoleIndex) { _, which ->
                selectedRoleIndex = which
            }
            .setPositiveButton("Join") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    val role = MemberRole.values()[selectedRoleIndex]
                    repository.joinChama(chama.id, name, role, userEmail)
                    showSnackbar("Successfully joined ${chama.name}!", false)
                } else {
                    showSnackbar("Name is required to join", true)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showChamaListDialog() {
        val chamas = repository.getChamas()
        if (chamas.isEmpty()) {
            showSnackbar("No Chamas found. Create one first!", true)
            return
        }

        val names = chamas.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Chama Group")
            .setItems(names) { _, which ->
                showChamaManagementDialog(chamas[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showChamaManagementDialog(chama: Chama) {
        val options = arrayOf(
            "View Description & Roles",
            "Meeting Attendance",
            "Schedule Meeting (Meet/WhatsApp)",
            "Withdrawal Requests (Dual Auth)",
            "Merry-Go-Round Rotation",
            "Transaction History",
            "M-Pesa Audit Trail (Transparency)",
            "Member Ledgers"
        )

        AlertDialog.Builder(this)
            .setTitle(chama.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showChamaDetails(chama)
                    1 -> showAttendanceScreen(chama)
                    2 -> showScheduleMeetingDialog(chama)
                    3 -> showWithdrawalManagement(chama)
                    4 -> showMerryGoRoundDialog(chama)
                    5 -> showChamaTransactions(chama)
                    6 -> showAuditTrail(chama)
                    7 -> showMemberLedgers(chama)
                }
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun showAuditTrail(chama: Chama) {
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        val logs = StringBuilder("--- Real-time M-Pesa Audit Trail ---\n\n")
        chama.transactions.forEach {
            logs.append("[TX] ${sdf.format(it.date)}: ${it.description} - KES ${it.amount}\n")
        }
        chama.withdrawalRequests.forEach {
            logs.append("[AUTH] Req: ${it.reason} | Approved by Chair: ${it.chairpersonApproved} | Approved by Member: ${it.memberApproved} | Status: ${it.status}\n")
        }
        if (chama.transactions.isEmpty() && chama.withdrawalRequests.isEmpty()) {
            logs.append("No audit logs found.")
        }
        AlertDialog.Builder(this).setTitle("Audit Trail: ${chama.name}").setMessage(logs.toString()).setPositiveButton("Close", null).show()
    }

    private fun showScheduleMeetingDialog(chama: Chama) {
        val options = arrayOf("Google Meet", "WhatsApp Call")
        AlertDialog.Builder(this)
            .setTitle("Schedule Meeting")
            .setItems(options) { _, which ->
                val mType = if (which == 0) MeetingType.GOOGLE_MEET else MeetingType.WHATSAPP_CALL
                val meeting = repository.scheduleMeeting(chama.id, "Group Meeting", mType)
                val msg = "Fincoop: New Chama Meeting scheduled. Join here: ${meeting.meetingLink}"
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, msg)
                    type = "text/plain"
                }
                startActivity(Intent.createChooser(sendIntent, "Send Meeting Link via..."))
            }
            .show()
    }

    private fun showWithdrawalManagement(chama: Chama) {
        val options = arrayOf("Request New Withdrawal", "View Pending Approvals")
        AlertDialog.Builder(this)
            .setTitle("Chama Withdrawals")
            .setItems(options) { _, which ->
                if (which == 0) showRequestWithdrawalDialog(chama)
                else showPendingApprovalsDialog(chama)
            }
            .show()
    }

    private fun showRequestWithdrawalDialog(chama: Chama) {
        val amountInput = EditText(this).apply { hint = "Amount"; inputType = InputType.TYPE_CLASS_NUMBER }
        val reasonInput = EditText(this).apply { hint = "Reason" }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 0)
            addView(amountInput)
            addView(reasonInput)
        }
        AlertDialog.Builder(this).setTitle("Request Withdrawal").setView(container).setPositiveButton("Submit") { _, _ ->
            val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
            val reason = reasonInput.text.toString()
            val userEmail = intent.getStringExtra("USER_EMAIL") ?: "User"
            repository.requestWithdrawal(chama.id, amount, repository.getUserPhone(userEmail), reason, userEmail)
            showSnackbar("Withdrawal request submitted for approval", false)
        }.show()
    }

    private fun showPendingApprovalsDialog(chama: Chama) {
        val pending = chama.withdrawalRequests.filter { it.status == "PENDING" }
        if (pending.isEmpty()) {
            showSnackbar("No pending withdrawal requests", false)
            return
        }
        val items = pending.map { "${it.reason}: KES ${it.amount}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Approve Withdrawals").setItems(items) { _, index ->
            val req = pending[index]
            val userEmail = intent.getStringExtra("USER_EMAIL") ?: "User"
            val isAdmin = chama.members.find { it.userId == userEmail }?.role == MemberRole.CHAIRPERSON
            AlertDialog.Builder(this).setTitle("Approve Request").setMessage("Details: ${req.reason}\nAmount: ${req.amount}\n\nApprove this transaction?")
                .setPositiveButton("Approve") { _, _ ->
                    repository.approveWithdrawal(chama.id, req.id, userEmail, isAdmin)
                    showSnackbar("Approval logged. Dual authentication required to execute.", false)
                }.show()
        }.show()
    }

    private fun showMerryGoRoundDialog(chama: Chama) {
        val nextMember = repository.getNextMerryGoRound(chama.id)
        val cycleInfo = "Current recipient: ${nextMember?.name ?: "None"}\nOrder: Registered first to last."
        AlertDialog.Builder(this).setTitle("Merry-Go-Round Cycle").setMessage(cycleInfo).setPositiveButton("Mark Cycle Complete") { _, _ ->
            repository.completeMerryGoRoundCycle(chama.id)
            showSnackbar("Rotation updated to next member", false)
        }.setNegativeButton("Close", null).show()
    }

    private fun showChamaDetails(chama: Chama) {
        val membersList = chama.members.joinToString("\n") { "${it.name} (${it.role})" }
        AlertDialog.Builder(this).setTitle("Chama Info: ${chama.name}").setMessage("Description: ${chama.description}\n\nMembers:\n$membersList").setPositiveButton("Close", null).show()
    }

    private fun showAttendanceScreen(chama: Chama) {
        val members = chama.members ?: emptyList()
        if (members.isEmpty()) {
            showSnackbar("No members in this Chama", true)
            return
        }
        val memberNames = members.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Mark Attendance - ${SimpleDateFormat("dd/MM/yy").format(java.util.Date())}").setItems(memberNames) { _, index ->
            val statusOptions = AttendanceStatus.values().map { it.name.replace("_", " ") }.toTypedArray()
            AlertDialog.Builder(this).setTitle("Status for ${memberNames[index]}").setItems(statusOptions) { _, sIndex ->
                val status = AttendanceStatus.values()[sIndex]
                val member = members[index]
                member.attendance[SimpleDateFormat("yyyy-MM-dd").format(java.util.Date())] = status
                repository.updateChamaMember(chama.id, member)
                showSnackbar("Attendance updated for ${member.name}", false)
            }.show()
        }.setPositiveButton("Done", null).show()
    }

    private fun showChamaTransactions(chama: Chama) {
        if (chama.transactions.isEmpty()) {
            showSnackbar("No transactions found for this Chama", false)
            return
        }
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
        val history = chama.transactions.joinToString("\n") { "${sdf.format(it.date)} - ${it.description}: KES ${it.amount}" }
        AlertDialog.Builder(this).setTitle("${chama.name} History").setMessage(history).setPositiveButton("Close", null).show()
    }

    private fun showMemberLedgers(chama: Chama) {
        val memberNames = chama.members.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Select Member Ledger").setItems(memberNames) { _, index ->
            showLedgerDetails(chama, chama.members[index])
        }.show()
    }

    private fun showLedgerDetails(chama: Chama, member: ChamaMember) {
        val ledgerInfo = "Name: ${member.name}\nSavings: KES ${member.balance}\nLoan Balance: KES ${member.loanBalance}\nInterest Due: KES ${member.loanBalance * 0.1}"
        AlertDialog.Builder(this).setTitle("Ledger: ${member.name}").setMessage(ledgerInfo).setPositiveButton("Partial Payment") { _, _ ->
            showPartialPaymentDialog(chama, member)
        }.setNeutralButton("Restructure Loan") { _, _ ->
            showRestructureDialog(chama, member)
        }.setNegativeButton("Close", null).show()
    }

    private fun showPartialPaymentDialog(chama: Chama, member: ChamaMember) {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this).setTitle("Log Payment").setView(input).setPositiveButton("Update") { _, _ ->
            val amount = input.text.toString().toDoubleOrNull() ?: 0.0
            member.loanBalance -= amount
            repository.updateChamaMember(chama.id, member)
            showSnackbar("Balance updated. New Balance: KES ${member.loanBalance}", false)
        }.show()
    }

    private fun showRestructureDialog(chama: Chama, member: ChamaMember) {
        showSnackbar("Loan Restructuring module opened for ${member.name}", false)
    }

    private fun showSnackbar(message: String, isError: Boolean) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
        if (isError) snackbar.setBackgroundTint(getColor(android.R.color.holo_red_dark))
        snackbar.show()
    }

    private fun setupBottomNavigation() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
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
}

class TransactionAdapter(
    private val onLongClick: ((Transaction) -> Unit)? = null,
    private val onMapClick: ((Transaction) -> Unit)? = null
) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {
    private var items = listOf<Transaction>()
    fun submitList(newItems: List<Transaction>) { items = newItems; notifyDataSetChanged() }
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.txtTransactionTitle)
        val date: TextView = view.findViewById(R.id.txtTransactionDate)
        val amount: TextView = view.findViewById(R.id.txtTransactionAmount)
        val btnMap: View? = view.findViewById(R.id.btnViewMap)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.description
        holder.date.text = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(item.date)
        val isPositive = item.type == TransactionType.DEPOSIT
        holder.amount.text = "${if (isPositive) "+" else "-"}KES ${String.format("%,.0f", item.amount)}"
        holder.amount.setTextColor(if (isPositive) 0xFF2E7D32.toInt() else 0xFFD32F2F.toInt())
        holder.itemView.setOnLongClickListener { onLongClick?.invoke(item); true }
        
        holder.btnMap?.visibility = if (item.lat != null) View.VISIBLE else View.GONE
        holder.btnMap?.setOnClickListener { onMapClick?.invoke(item) }
    }
    override fun getItemCount() = items.size
}
