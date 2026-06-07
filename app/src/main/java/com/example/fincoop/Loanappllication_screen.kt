package com.example.fincoop

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LoanApplicationActivity : AppCompatActivity() {

    private lateinit var etAmount: EditText
    private lateinit var etMonths: EditText
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etIncome: EditText
    private lateinit var etReason: EditText
    private lateinit var tvResult: TextView
    private lateinit var tvStatus: TextView
    private lateinit var cbTerms: CheckBox

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

            tvStatus.text = getString(R.string.loan_applied_success)
        }
    }
}
