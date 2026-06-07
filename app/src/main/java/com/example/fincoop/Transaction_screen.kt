package com.example.fincoop

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class TransactionHistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_screen)

        val listView = findViewById<ListView>(R.id.listTransactions)

        val transactions = arrayOf(
            "Deposit - KES 5,000 - Completed",
            "Loan Repayment - KES 2,500 - Completed",
            "Withdrawal - KES 1,000 - Pending",
            "Deposit - KES 3,000 - Completed",
            "Transfer - KES 800 - Completed"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            transactions
        )

        listView.adapter = adapter
    }
}