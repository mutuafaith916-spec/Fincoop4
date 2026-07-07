package com.example.fincoop

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.LocationServices
import com.google.android.material.snackbar.Snackbar

class LoanApplicationActivity : SecureActivity() {

    private lateinit var etAmount: EditText
    private lateinit var etMonths: EditText
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etIncome: EditText
    private lateinit var etReason: EditText
    private lateinit var tvResult: TextView
    private lateinit var tvStatus: TextView
    private lateinit var cbTerms: CheckBox
    private val repository by lazy { FincoopRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loanapllication_screen)

        etAmount = findViewById(R.id.etAmount)
        etMonths = findViewById(R.id.etMonths)
        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etIncome = findViewById(R.id.etIncome)
        etReason = findViewById(R.id.etReason)
        tvResult = findViewById(R.id.tvResult)
        tvStatus = findViewById(R.id.tvStatus)
        cbTerms = findViewById(R.id.cbTerms)

        val btnCalculate: Button = findViewById(R.id.btnCalculate)
        val btnApply: Button = findViewById(R.id.btnApply)

        // LOAN CALCULATOR (simple interest model)
        btnCalculate.setOnClickListener {
            val amount = etAmount.text.toString().toDoubleOrNull()
            val months = etMonths.text.toString().toIntOrNull()

            if (amount == null || months == null || months <= 0) {
                tvResult.text = getString(R.string.error_invalid_input)
                return@setOnClickListener
            }

            val interestRate = 0.10 // 10% per month (example)
            val totalInterest = amount * interestRate * months
            val total = amount + totalInterest
            val monthly = total / months

            tvResult.text = getString(R.string.loan_result_format, monthly, total)
        }

        // APPLY BUTTON
        btnApply.setOnClickListener {
            val name = etName.text.toString()
            val phone = etPhone.text.toString()
            val income = etIncome.text.toString()
            val reason = etReason.text.toString()

            if (!cbTerms.isChecked) {
                tvStatus.text = getString(R.string.error_accept_terms)
                return@setOnClickListener
            }

            if (name.isEmpty() || phone.isEmpty() || income.isEmpty() || reason.isEmpty()) {
                tvStatus.text = getString(R.string.error_fill_details)
                return@setOnClickListener
            }

            // Record location and save loan "transaction"
            val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
            
            if (PermissionHelper.hasLocationPermission(this)) {
                if (PermissionHelper.isLocationEnabled(this)) {
                    LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { location ->
                        val lat = location?.latitude
                        val lng = location?.longitude
                        
                        if (lat != null && lng != null) {
                            repository.saveCurrentLocation(lat, lng)
                        }

                        val transaction = Transaction(
                            type = TransactionType.CHAMA_CONTRIBUTION, // or a LOAN type if available
                            amount = amount,
                            description = "Loan Application: $reason",
                            lat = lat,
                            lng = lng
                        )
                        repository.addTransaction(transaction)
                        tvStatus.text = getString(R.string.loan_applied_success) + "\n(Location recorded)"
                    }
                } else {
                    Snackbar.make(btnApply, "Please turn on your GPS to apply for a loan.", Snackbar.LENGTH_LONG)
                        .setAction("Turn On") {
                            startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }.show()
                }
            } else {
                PermissionHelper.requestLocationPermissions(this, 1001)
                Snackbar.make(btnApply, "Location access is required to apply for a loan.", Snackbar.LENGTH_LONG).show()
            }
        }
    }
}
