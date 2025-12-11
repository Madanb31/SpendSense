package com.example.spendsense

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.spendsense.database.AppDatabase
import com.example.spendsense.database.Transaction
import com.example.spendsense.utils.CurrencyHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportsFragment : Fragment() {

    private lateinit var database: AppDatabase
    private var userId: Int = -1

    // Data holders for export
    private var totalIncome = 0.0
    private var totalExpense = 0.0
    private var categoryData = listOf<Pair<String, Double>>()
    private var topDaysData = listOf<Pair<String, Double>>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_reports, container, false)

        database = AppDatabase.getDatabase(requireContext())
        val prefs = requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE)
        userId = prefs.getInt("userId", -1)

        // Load Data
        loadReportsData(view)

        // Buttons
        view.findViewById<Button>(R.id.btn_share_report).setOnClickListener { shareReport() }
        view.findViewById<FloatingActionButton>(R.id.fab_export_pdf).setOnClickListener { exportToPdf() }

        return view
    }

    private fun loadReportsData(view: View) {
        if (userId == -1) return

        val tvMonthYear = view.findViewById<TextView>(R.id.tv_month_year)
        val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        tvMonthYear.text = dateFormat.format(Date())

        lifecycleScope.launch {
            database.transactionDao().getAllTransactions(userId).collect { transactions ->
                calculateReports(view, transactions)
            }
        }
    }

    private fun calculateReports(view: View, transactions: List<Transaction>) {
        // ADD THIS
        val symbol = com.example.spendsense.utils.CurrencyHelper.getSymbol(requireContext())

        // 1. Totals
        totalIncome = transactions.filter { it.type == "income" }.sumOf { it.amount }
        totalExpense = transactions.filter { it.type == "expense" }.sumOf { it.amount }
        val savings = totalIncome - totalExpense

        // UPDATE THESE with symbol
        view.findViewById<TextView>(R.id.tv_income_val).text = "$symbol${String.format("%.0f", totalIncome)}"
        view.findViewById<TextView>(R.id.tv_expense_val).text = "$symbol${String.format("%.0f", totalExpense)}"
        view.findViewById<TextView>(R.id.tv_savings_val).text = "$symbol${String.format("%.0f", savings)}"

        // 2. Chart Bar
        view.findViewById<TextView>(R.id.tv_chart_income).text = "$symbol${String.format("%.0f", totalIncome)}"
        view.findViewById<TextView>(R.id.tv_chart_expense).text = "$symbol${String.format("%.0f", totalExpense)}"

        // ... (Chart logic stays same) ...

        // 3. Categories Loop
        // ...
        val categoryContainer = view.findViewById<LinearLayout>(R.id.ll_reports_categories)
        categoryContainer.removeAllViews() // Now it works

        for ((name, amount) in categoryData) {
            val percent = if (totalExpense > 0) (amount / totalExpense * 100).toInt() else 0
            val row = TextView(context)
            // UPDATE THIS
            row.text = "$name: $symbol${String.format("%.0f", amount)} ($percent%)"
            row.textSize = 14f
            row.setPadding(0, 8, 0, 8)
            categoryContainer.addView(row)
        }

        // 4. Top Days Loop
        // ...

        // DEFINE VARIABLE HERE
        val daysContainer = view.findViewById<LinearLayout>(R.id.ll_top_days)
        daysContainer.removeAllViews() // Now it works

        for ((date, amount) in topDaysData) {
            val row = TextView(context)
            // UPDATE THIS
            row.text = "$date: $symbol${String.format("%.0f", amount)}"
            row.textSize = 14f
            row.setPadding(0, 8, 0, 8)
            daysContainer.addView(row)
        }
    }

    private fun shareReport() {
        val reportText = """
            📊 SpendSense Report
            Income: ₹$totalIncome
            Expense: ₹$totalExpense
            Savings: ₹${totalIncome - totalExpense}
            
            Top Categories:
            ${categoryData.take(3).joinToString("\n") { "${it.first}: ₹${it.second}" }}
        """.trimIndent()

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SpendSense Report")
            putExtra(Intent.EXTRA_TEXT, reportText)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Report via"))
    }

    private fun exportToPdf() {
        try {
            val symbol = com.example.spendsense.utils.CurrencyHelper.getSymbol(requireContext())

            val fileName = "SpendSense_Report_${System.currentTimeMillis()}.pdf"
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            val pdfWriter = PdfWriter(file)
            val pdfDocument = PdfDocument(pdfWriter)
            val document = Document(pdfDocument)

            // Title
            document.add(Paragraph("SpendSense Report").setFontSize(24f).setBold())

            // Financial Summary
            document.add(Paragraph("\nFinancial Summary").setFontSize(18f).setBold())
            document.add(Paragraph("Income: $symbol${String.format("%.0f", totalIncome)}"))
            document.add(Paragraph("Expense: $symbol${String.format("%.0f", totalExpense)}"))
            document.add(Paragraph("Savings: $symbol${String.format("%.0f", totalIncome - totalExpense)}"))

            // Spending by Category
            document.add(Paragraph("\nSpending by Category").setFontSize(18f).setBold())

            val table = Table(2) // 2 Columns
            table.addCell(Paragraph("Category").setBold())
            table.addCell(Paragraph("Amount").setBold())

            for ((name, amount) in categoryData) {
                table.addCell(name)
                table.addCell("$symbol${String.format("%.0f", amount)}")
            }
            document.add(table)

            // Footer
            document.add(Paragraph("\nGenerated by SpendSense").setFontSize(10f).setItalic())

            document.close()
            Toast.makeText(context, "PDF Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()

            // Open PDF logic
            try {
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, "application/pdf")
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                startActivity(Intent.createChooser(intent, "Open Report"))
            } catch (e: Exception) {
                // Ignore open error if no PDF viewer installed
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error creating PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}