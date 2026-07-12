package com.example.myapplication.utils

import java.text.SimpleDateFormat
import java.util.*

object HolidayTracker {

    data class SpecialEvent(
        val name: String,
        val multiplier: Double,
        val description: String
    )

    private val fixedHolidays = mapOf(
        "01-01" to SpecialEvent("New Year's Day", 2.5, "Peak holiday waste volume expected."),
        "01-02" to SpecialEvent("New Year Extension", 1.8, "Post-New Year cleanup in progress."),
        "02-14" to SpecialEvent("Valentine's Day", 1.2, "Slight increase in commercial waste."),
        "04-09" to SpecialEvent("Araw ng Kagitingan", 1.3, "National holiday; moderate volume increase."),
        "05-01" to SpecialEvent("Labor Day", 1.4, "Public holiday events may increase waste."),
        "06-12" to SpecialEvent("Independence Day", 1.4, "Public celebrations expected."),
        "08-21" to SpecialEvent("Ninoy Aquino Day", 1.2, "Special non-working day."),
        "08-28" to SpecialEvent("National Heroes Day", 1.3, "Holiday volume observed."),
        "11-01" to SpecialEvent("All Saints' Day (Undas)", 1.8, "High volume near cemeteries and public areas."),
        "11-02" to SpecialEvent("All Souls' Day", 1.5, "Cleanup after Undas commemorations."),
        "11-30" to SpecialEvent("Bonifacio Day", 1.3, "Holiday volume observed."),
        "12-08" to SpecialEvent("Feast of the Immaculate Conception", 1.3, "Religious holiday events."),
        "12-24" to SpecialEvent("Christmas Eve", 2.0, "High preparation waste for Noche Buena."),
        "12-25" to SpecialEvent("Christmas Day", 2.5, "Peak holiday waste volume."),
        "12-30" to SpecialEvent("Rizal Day", 1.5, "Year-end holiday volume."),
        "12-31" to SpecialEvent("New Year's Eve", 2.2, "High preparation waste for Media Noche.")
    )

    fun getEventForDate(dateStr: String): SpecialEvent? {
        // Date format assumed: yyyy-MM-dd
        if (dateStr.length < 10) return null
        val monthDay = dateStr.substring(5) // MM-dd
        
        // 1. Check Fixed Holidays
        fixedHolidays[monthDay]?.let { return it }

        // 2. Check Seasonal Ranges (e.g. Christmas Season starts early in PH)
        val month = dateStr.substring(5, 7).toIntOrNull() ?: 0
        val day = dateStr.substring(8, 10).toIntOrNull() ?: 0

        if (month == 12 && day in 16..23) {
            return SpecialEvent("Simbang Gabi / Pre-Christmas", 1.4, "Early Christmas season festivities.")
        }
        
        if (month == 12 && day in 26..29) {
            return SpecialEvent("Post-Christmas Cleanup", 1.6, "Waste volume remains high after Christmas.")
        }

        // 3. Check for Weekends
        if (isWeekend(dateStr)) {
            return SpecialEvent("Weekend", 1.2, "Normal weekend volume increase.")
        }

        return null
    }

    private fun isWeekend(dateStr: String): Boolean {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(dateStr) ?: return false
            val cal = Calendar.getInstance().apply { time = date }
            val day = cal.get(Calendar.DAY_OF_WEEK)
            day == Calendar.SATURDAY || day == Calendar.SUNDAY
        } catch (e: Exception) {
            false
        }
    }
}
