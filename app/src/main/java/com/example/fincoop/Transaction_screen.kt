package com.example.fincoop

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TransactionHistoryActivity : AppCompatActivity() {

    private lateinit var adapter: TransactionAdapter
    private lateinit var repository: FincoopRepository
    private var allTransactions = listOf<Transaction>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_screen)

        repository = FincoopRepository(this)
        setupUI()
    }

    private fun setupUI() {
        val rv = findViewById<RecyclerView>(R.id.rvTransactionsFull)
        adapter = TransactionAdapter()
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        allTransactions = repository.getTransactions()
        updateList(allTransactions)

        findViewById<View>(R.id.toolbar).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterTransactions(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterTransactions(query: String) {
        val filtered = if (query.isEmpty()) {
            allTransactions
        } else {
            allTransactions.filter { 
                it.description.contains(query, ignoreCase = true) || 
                it.type.name.contains(query, ignoreCase = true) 
            }
        }
        updateList(filtered)
    }

    private fun updateList(list: List<Transaction>) {
        adapter.submitList(list)
        findViewById<TextView>(R.id.txtEmpty).visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }
}
