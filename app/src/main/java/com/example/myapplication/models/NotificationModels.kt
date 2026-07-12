package com.example.myapplication.models

data class SystemNotification(
    var id: String = "",
    var type: String = "", // COMPLAINT, REGISTRATION, DRIVER_ISSUE, TRUCK_FULL, TRIP_COMPLETED
    var title: String = "",
    var message: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    var isRead: Boolean = false,
    var relatedId: String = "", // ID of the complaint, user, or report
    var status: String = "PENDING",
    var adminResponse: String? = null,
    var userId: Long? = null // Target user ID for the notification
)

data class DriverIssue(
    val id: String = "",
    val driverName: String = "",
    val truckId: String = "",
    val issueType: String = "",
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING" // PENDING, RESOLVED
)
