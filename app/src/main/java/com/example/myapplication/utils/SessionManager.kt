package com.example.myapplication.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.myapplication.models.UserData
import com.google.gson.Gson

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_USER_DATA = "user_data"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_APP_NOTIFICATIONS = "app_notifications_enabled"
        private const val KEY_EMAIL_NOTIFICATIONS = "email_notifications_enabled"
        private const val KEY_AUTO_BACKUP = "auto_backup_enabled"
        
        // Trip State Keys
        private const val KEY_TRIP_START_TIME = "trip_start_time"
        private const val KEY_TRIP_DISTANCE = "trip_distance"
        private const val KEY_VISITED_ZONES = "visited_zones"
        private const val KEY_TRUCK_MILEAGE = "truck_mileage"
        
        // Truck Details Keys
        private const val KEY_TRUCK_PLATE = "truck_plate"
        private const val KEY_TRUCK_MODEL = "truck_model"
        private const val KEY_TRUCK_FUEL = "truck_fuel"
        private const val KEY_TRUCK_CAPACITY = "truck_capacity"
    }

    fun saveTruckDetails(truckId: String, plate: String, model: String, fuel: String, capacity: String) {
        val editor = prefs.edit()
        editor.putString(KEY_TRUCK_PLATE, plate)
        editor.putString(KEY_TRUCK_MODEL, model)
        editor.putString(KEY_TRUCK_FUEL, fuel)
        editor.putString(KEY_TRUCK_CAPACITY, capacity)
        editor.apply()

        // Also update preferredTruck in UserData
        getUser()?.let { user ->
            val updatedUser = user.copy(preferredTruck = truckId)
            saveUser(updatedUser)
        }
    }

    fun getTruckDetails(): Map<String, String> {
        return mapOf(
            "plate" to (prefs.getString(KEY_TRUCK_PLATE, "ABC 1234") ?: "ABC 1234"),
            "model" to (prefs.getString(KEY_TRUCK_MODEL, "Hino 500 Series") ?: "Hino 500 Series"),
            "fuel" to (prefs.getString(KEY_TRUCK_FUEL, "Diesel") ?: "Diesel"),
            "capacity" to (prefs.getString(KEY_TRUCK_CAPACITY, "10 Tons") ?: "10 Tons")
        )
    }

    fun saveTruckMileage(mileage: Float) {
        prefs.edit().putFloat(KEY_TRUCK_MILEAGE, mileage).apply()
    }

    fun getTruckMileage(): Float {
        return prefs.getFloat(KEY_TRUCK_MILEAGE, 0f)
    }

    fun saveTripStartTime(time: Long) {
        prefs.edit().putLong(KEY_TRIP_START_TIME, time).apply()
    }

    fun getTripStartTime(): Long {
        return prefs.getLong(KEY_TRIP_START_TIME, 0L)
    }

    fun saveTripDistance(distance: Float) {
        prefs.edit().putFloat(KEY_TRIP_DISTANCE, distance).apply()
    }

    fun getTripDistance(): Float {
        return prefs.getFloat(KEY_TRIP_DISTANCE, 0f)
    }

    fun saveVisitedZones(zones: Set<String>) {
        prefs.edit().putStringSet(KEY_VISITED_ZONES, zones).apply()
    }

    fun getVisitedZones(): MutableSet<String> {
        return prefs.getStringSet(KEY_VISITED_ZONES, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    fun clearTripData() {
        prefs.edit()
            .remove(KEY_TRIP_START_TIME)
            .remove(KEY_TRIP_DISTANCE)
            .remove(KEY_VISITED_ZONES)
            .apply()
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
    }

    fun isAutoBackupEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_BACKUP, true)
    }

    fun setAppNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_NOTIFICATIONS, enabled).apply()
    }

    fun isAppNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_APP_NOTIFICATIONS, true)
    }

    fun setEmailNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EMAIL_NOTIFICATIONS, enabled).apply()
    }

    fun isEmailNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_EMAIL_NOTIFICATIONS, true)
    }

    fun saveUser(user: UserData) {
        val editor = prefs.edit()
        editor.putString(KEY_USER_DATA, gson.toJson(user))
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.apply()
    }

    fun getUser(): UserData? {
        val userData = prefs.getString(KEY_USER_DATA, null)
        return if (userData != null) {
            gson.fromJson(userData, UserData::class.java)
        } else {
            null
        }
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun logout() {
        val editor = prefs.edit()
        editor.clear()
        editor.apply()
    }
}