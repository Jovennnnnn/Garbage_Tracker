package com.example.myapplication.utils

import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.myapplication.R
import com.example.myapplication.models.Complaint
import com.example.myapplication.models.UserData
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object DataExporter {

    fun exportFullSystemDataPDF(
        context: Context,
        residents: List<UserData>,
        drivers: List<UserData>,
        complaints: List<Complaint>
    ) {
        val inflater = LayoutInflater.from(context)
        val root = inflater.inflate(R.layout.layout_system_data_export, null)

        // 1. Basic Info
        val sdf = SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.getDefault())
        root.findViewById<TextView>(R.id.tv_pdf_generated_at).text = "Generated on: ${sdf.format(Date())}"

        // 2. Stats
        root.findViewById<TextView>(R.id.tv_pdf_stat_residents).text = "Total Residents: ${residents.size}"
        root.findViewById<TextView>(R.id.tv_pdf_stat_drivers).text = "Total Drivers: ${drivers.size}"
        root.findViewById<TextView>(R.id.tv_pdf_stat_complaints).text = "Total Complaints: ${complaints.size}"
        val pending = complaints.count { it.status.lowercase() == "pending" }
        root.findViewById<TextView>(R.id.tv_pdf_stat_pending).text = "Pending Issues: $pending"

        // 3. Intelligent Summary
        val resolutionRate = if (complaints.isNotEmpty()) {
            ((complaints.size - pending).toFloat() / complaints.size * 100).toInt()
        } else 100
        
        val summaryText = "System Health: The database is currently managing ${residents.size + drivers.size} total users. " +
                "With a complaint resolution rate of $resolutionRate%, the administrative response is " +
                (if (resolutionRate > 80) "highly efficient." else "active but requires attention.") +
                " Most registered residents are from the " + (residents.groupBy { it.purok }.maxByOrNull { it.value.size }?.key ?: "various") + " areas."
        
        root.findViewById<TextView>(R.id.tv_pdf_intelligent_summary).text = summaryText

        // 4. Populate Lists (Dynamic injection)
        populateList(context, root.findViewById(R.id.container_residents), residents.map { "${it.name} (${it.purok ?: "N/A"})" })
        populateList(context, root.findViewById(R.id.container_drivers), drivers.map { "${it.name} [License: ${it.licenseNumber ?: "N/A"}]" })
        populateList(context, root.findViewById(R.id.container_complaints), complaints.take(15).map { 
            "[${it.status.uppercase()}] ${it.fullName ?: "Anon"}: ${it.description.take(50)}${if(it.description.length > 50) "..." else ""}" 
        })

        // --- PDF Generation Logic ---
        val width = 595 // A4 width in pts
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val height = root.measuredHeight
        root.layout(0, 0, width, height)

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(width, height, 1).create()
        val page = document.startPage(pageInfo)
        
        root.draw(page.canvas)
        document.finishPage(page)

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val fileName = "System_Export_${System.currentTimeMillis()}.pdf"
        val file = File(downloadsDir, fileName)

        try {
            document.writeTo(FileOutputStream(file))
            Toast.makeText(context, "Professional Export Saved to Downloads", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Export Error: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            document.close()
        }
    }

    private fun populateList(context: Context, container: LinearLayout, items: List<String>) {
        container.removeAllViews()
        if (items.isEmpty()) {
            val tv = TextView(context)
            tv.text = "No records found."
            tv.textSize = 9f
            tv.setTextColor(Color.GRAY)
            container.addView(tv)
            return
        }

        items.forEach { text ->
            val tv = TextView(context)
            tv.text = "• $text"
            tv.textSize = 10f
            tv.setTextColor(Color.parseColor("#333333"))
            tv.setPadding(0, 2, 0, 2)
            container.addView(tv)
        }
    }
}
