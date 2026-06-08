package com.example.fincoop

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private val repository by lazy { FincoopRepository(this) }
    private val viewModel: DashboardViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DashboardViewModel(repository) as T
            }
        }
    }

    private lateinit var transactionAdapter: TransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        setupUI()
        observeViewModel()
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
        val defaultName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
        val name = repository.getUserName(defaultName)
        findViewById<TextView>(R.id.txtWelcome).text = "Welcome, $name"
        
        // Set profile image if available
        val imageUri = repository.getUserImage()
        imageUri?.let {
            try {
                findViewById<ImageView>(R.id.profileImage).setImageURI(android.net.Uri.parse(it))
            } catch (e: Exception) {}
        }
    }

    override fun onResume() {
        super.onResume()
        setupWelcomeMessage() // Refresh name if updated in Profile
        viewModel.loadData()
    }

    private fun setupRecyclerView() {
        val rv = findViewById<RecyclerView>(R.id.rvTransactions)
        transactionAdapter = TransactionAdapter()
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
            showTransactionDialog("Deposit", "Enter amount to deposit") { amount ->
                confirmAndAuthenticate("Deposit", "You are about to deposit KES ${String.format("%,.0f", amount)}.") {
                    viewModel.processTransaction(TransactionType.DEPOSIT, amount, "Deposit to Savings")
                }
            }
        }

        findViewById<View>(R.id.actionWithdraw).setOnClickListener {
            showTransactionDialog("Withdraw", "Enter amount to withdraw") { amount ->
                confirmAndAuthenticate("Withdrawal", "You are about to withdraw KES ${String.format("%,.0f", amount)}.") {
                    viewModel.processTransaction(TransactionType.WITHDRAWAL, amount, "Withdrawal from Savings")
                }
            }
        }

        findViewById<View>(R.id.actionTransfer).setOnClickListener {
            showTransferDialog { amount, phone ->
                confirmAndAuthenticate("Transfer", "You are about to transfer KES ${String.format("%,.0f", amount)} to $phone.") {
                    viewModel.processTransaction(TransactionType.TRANSFER, amount, "Transfer to $phone")
                }
            }
        }

        findViewById<View>(R.id.actionLoan).setOnClickListener {
            startActivity(Intent(this, LoanApplicationActivity::class.java))
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
                if (repository.verifyPin(pin)) {
                    onAuthenticated()
                } else {
                    showSnackbar("Incorrect PIN", true)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTransactionDialog(title: String, message: String, onConfirm: (Double) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Amount"
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 0)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setView(container)
            .setPositiveButton("Continue") { _, _ ->
                val amount = input.text.toString().toDoubleOrNull()
                if (amount != null && amount > 0) onConfirm(amount)
                else showSnackbar("Invalid amount", true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTransferDialog(onConfirm: (Double, String) -> Unit) {
        val phoneInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            hint = "Phone Number"
        }
        val amountInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Amount"
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 0)
            addView(phoneInput)
            addView(amountInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Transfer Funds")
            .setView(container)
            .setPositiveButton("Continue") { _, _ ->
                val phone = phoneInput.text.toString()
                val amount = amountInput.text.toString().toDoubleOrNull()
                if (phone.length >= 10 && amount != null && amount > 0) onConfirm(amount, phone)
                else showSnackbar("Invalid details", true)
            }
            .setNegativeButton("Cancel", null)
            .show()
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

// --- Adapter for Transactions ---
class TransactionAdapter(
    private val onLongClick: ((Transaction) -> Unit)? = null
) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {
    private var items = listOf<Transaction>()

    fun submitList(newItems: List<Transaction>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.txtTransactionTitle)
        val date: TextView = view.findViewById(R.id.txtTransactionDate)
        val amount: TextView = view.findViewById(R.id.txtTransactionAmount)
        val icon: View = view.findViewById(R.id.imgTransactionIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.description
        holder.date.text = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(item.date)
        
        val isPositive = item.type == TransactionType.DEPOSIT
        holder.amount.text = "${if (isPositive) "+" else "-"}KES ${String.format("%,.0f", item.amount)}"
        holder.amount.setTextColor(if (isPositive) 0xFF2E7D32.toInt() else 0xFFD32F2F.toInt())

        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(item)
            true
        }
    }

    override fun getItemCount() = items.size
}
