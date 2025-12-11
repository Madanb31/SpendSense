package com.example.spendsense

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.spendsense.utils.CurrencyHelper
import com.example.spendsense.database.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private var transactions = emptyList<Transaction>()

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: TextView = itemView.findViewById(R.id.tv_icon)
        val description: TextView = itemView.findViewById(R.id.tv_description)
        val categoryDate: TextView = itemView.findViewById(R.id.tv_category_date)
        val amount: TextView = itemView.findViewById(R.id.tv_amount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val current = transactions[position]
        val context = holder.itemView.context

        // ADD THIS: Get symbol
        val symbol = com.example.spendsense.utils.CurrencyHelper.getSymbol(context)

        holder.icon.text = current.categoryIcon
        holder.description.text = current.description

        val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        val dateString = dateFormat.format(Date(current.date))
        holder.categoryDate.text = "${current.categoryName} • $dateString"

        // UPDATE THIS BLOCK
        if (current.type == "expense") {
            holder.amount.text = "- $symbol${String.format("%.0f", current.amount)}" // Use symbol
            holder.amount.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
        } else {
            holder.amount.text = "+ $symbol${String.format("%.0f", current.amount)}" // Use symbol
            holder.amount.setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
        }
    }

    override fun getItemCount() = transactions.size

    fun setData(newTransactions: List<Transaction>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }
}