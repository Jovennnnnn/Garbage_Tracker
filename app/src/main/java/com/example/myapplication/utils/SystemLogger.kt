package com.example.myapplication.utils

import com.example.myapplication.models.SystemLog
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

object SystemLogger {
    private const val DB_URL = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
    private val database = FirebaseDatabase.getInstance(DB_URL).getReference("system_logs")

    fun logEvent(type: String, message: String, details: String = "", adminName: String = "Admin") {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timestamp = System.currentTimeMillis()
        val date = sdf.format(Date(timestamp))

        val logId = database.push().key ?: return
        val log = SystemLog(
            id = logId,
            type = type,
            message = message,
            timestamp = timestamp,
            details = details,
            adminName = adminName,
            date = date
        )
        database.child(logId).setValue(log)
        
        // Auto-cleanup on every log (or periodically)
        cleanupOldLogs()
    }

    fun cleanupOldLogs() {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        database.orderByChild("timestamp").endAt(thirtyDaysAgo.toDouble())
            .get().addOnSuccessListener { snapshot ->
                for (child in snapshot.children) {
                    child.ref.removeValue()
                }
            }
    }
}
