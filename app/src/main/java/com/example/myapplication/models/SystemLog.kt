package com.example.myapplication.models

data class SystemLog(
    val id: String = "",
    val type: String = "",
    val message: String = "",
    val timestamp: Long = 0,
    val details: String = "",
    val adminName: String = "",
    val date: String = ""
)
