package com.example.myapplication

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.fragments.MapboxFragment
import com.example.myapplication.network.LocationUpdateService
import com.example.myapplication.utils.SessionManager
import com.example.myapplication.utils.GpsStatusMonitor
import com.example.myapplication.utils.PurokManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast
import com.google.firebase.database.*
import com.example.myapplication.models.SystemNotification
import com.example.myapplication.adapters.NotificationAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DriverDashboardActivity : AppCompatActivity(), MapboxFragment.OnTrucksUpdatedListener {

    private lateinit var sessionManager: SessionManager
    private lateinit var tvDriverName: TextView
    private lateinit var tvTruckId: TextView
    private lateinit var tvCurrentStatus: TextView
    
    // Trip Info Views
    private lateinit var tvPlateNumber: TextView
    private lateinit var tvStartTime: TextView
    private lateinit var tvEstimatedEnd: TextView
    private lateinit var tvTotalDistance: TextView
    
    // GPS Status Views
    private lateinit var llSignalStrength: View
    private lateinit var viewSignalIndicator: View
    private lateinit var tvSignalStrength: TextView
    private lateinit var tvGpsAccuracy: TextView
    private lateinit var tvGpsLastUpdate: TextView
    
    // Progress Views
    private lateinit var tvProgressText: TextView
    private lateinit var llCollectionStops: LinearLayout
    
    // Map Tab Live Tracking Views
    private lateinit var tvMapCurrentPurok: TextView
    private lateinit var tvMapTruckNumber: TextView
    private lateinit var tvMapPlateNumber: TextView
    private lateinit var tvMapStartTime: TextView
    private lateinit var tvMapTotalDistance: TextView
    private lateinit var llMapSignalStrength: View
    private lateinit var viewMapSignalIndicator: View
    private lateinit var tvMapSignalStrength: TextView
    private lateinit var tvMapGpsAccuracy: TextView
    private lateinit var tvMapSpeed: TextView
    
    private lateinit var layoutDashboard: View
    private lateinit var layoutMap: View
    private lateinit var layoutSettings: View
    private lateinit var bottomNav: BottomNavigationView

    // Settings tab views
    private lateinit var tvSettingsProfileName: TextView
    private lateinit var tvSettingsProfileContact: TextView
    private lateinit var tvSettingsProfileTruck: TextView
    
    private var mapFragment: MapboxFragment? = null
    private var activeDialog: AlertDialog? = null

    // Tracking state for Purok list optimization
    private var isViewAllPuroks = false
    private var lastLat: Double = 0.0
    private var lastLng: Double = 0.0
    private var lastCurrentZone: String? = null

    // Notifications
    private val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
    private val database = FirebaseDatabase.getInstance(dbUrl)
    private var notificationListener: ValueEventListener? = null
    private val notificationList = mutableListOf<SystemNotification>()
    private lateinit var badgeNotifications: TextView

    private val locationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationUpdateService.ACTION_LOCATION_UPDATE) {
                val lat = intent.getDoubleExtra(LocationUpdateService.EXTRA_LATITUDE, 0.0)
                val lng = intent.getDoubleExtra(LocationUpdateService.EXTRA_LONGITUDE, 0.0)
                val accuracy = intent.getDoubleExtra(LocationUpdateService.EXTRA_ACCURACY, 0.0)
                val distance = intent.getDoubleExtra(LocationUpdateService.EXTRA_DISTANCE, 0.0)
                val status = intent.getStringExtra(LocationUpdateService.EXTRA_STATUS) ?: "active"
                val currentZone = intent.getStringExtra(LocationUpdateService.EXTRA_CURRENT_ZONE)
                
                lastLat = lat
                lastLng = lng
                lastCurrentZone = currentZone

                updateGpsStatusUI(accuracy)
                updateTripMetricsUI(distance, status)
                updateCollectionProgressUI(currentZone, lat, lng)

                // Update Map tab specific fields
                tvMapCurrentPurok.text = currentZone ?: "Searching..."
                val speed = intent.getDoubleExtra(LocationUpdateService.EXTRA_SPEED, 0.0)
                tvMapSpeed.text = String.format(Locale.getDefault(), "%.0f km/h", speed)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Continuous GPS Monitoring
        lifecycle.addObserver(GpsStatusMonitor(this) { isEnabled ->
            if (!isEnabled) {
                mapFragment?.clearMap()
            }
        })

        enableEdgeToEdge()
        setContentView(R.layout.activity_driver_dashboard)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.driver_dashboard_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sessionManager = SessionManager(this)
        initializeViews()
        setupNavigation()
        setupStatusControls()
        setupSettingsTab()
        setupSettingsClickListeners()
        setupDemoControls()
        setupMap(isFullMode = false)
        setupRealtimeNotifications()
        
        // Register receiver
        val filter = IntentFilter(LocationUpdateService.ACTION_LOCATION_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(locationUpdateReceiver, filter)
        }

        checkLocationPermissions {
            // Automatically start service when dashboard opens (as requested)
            startGpsService("active")
        }
    }

    private fun startGpsService(status: String) {
        val serviceIntent = Intent(this, LocationUpdateService::class.java).apply {
            putExtra("status", status)
        }
        startService(serviceIntent)
        
        // Capture start time if not set
        if (sessionManager.getTripStartTime() == 0L) {
            sessionManager.saveTripStartTime(System.currentTimeMillis())
        }
        updateTripBasicInfo()
    }

    private fun setupDemoControls() {
        val user = sessionManager.getUser()
        val truckId = user?.preferredTruck ?: "GT-001"
        val driverName = user?.name ?: "Pedro Santos"
        val database = com.google.firebase.database.FirebaseDatabase.getInstance("https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app")

        findViewById<View>(R.id.btn_manual_alert).setOnClickListener {
            val zones = com.example.myapplication.utils.PurokManager.purokZones.map { it.name }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Send Manual Alert")
                .setItems(zones) { _, which ->
                    val selectedPurok = zones[which]
                    val alertData = mapOf(
                        "message" to "🚛 Manual Alert: The garbage truck is heading to $selectedPurok. Please prepare your trash!",
                        "timestamp" to System.currentTimeMillis(),
                        "driver" to driverName,
                        "truck_id" to truckId,
                        "type" to "MANUAL_ALERT"
                    )
                    database.getReference("alerts").child(selectedPurok).setValue(alertData)
                    
                    // Update Local Progress
                    val visited = sessionManager.getVisitedZones()
                    visited.add(selectedPurok)
                    sessionManager.saveVisitedZones(visited)
                    updateCollectionProgressUI(lastCurrentZone, lastLat, lastLng)

                    android.widget.Toast.makeText(this, "Alert sent to $selectedPurok", android.widget.Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        findViewById<View>(R.id.btn_demo_teleport).setOnClickListener {
            val zones = com.example.myapplication.utils.PurokManager.purokZones
            val zoneNames = zones.map { it.name }.toTypedArray()
            
            AlertDialog.Builder(this)
                .setTitle("Demo: Teleport Truck")
                .setItems(zoneNames) { _, which ->
                    val target = zones[which]
                    val truckData = mapOf(
                        "truckId" to truckId,
                        "driverName" to driverName,
                        "latitude" to target.latitude,
                        "longitude" to target.longitude,
                        "speed" to 20.0,
                        "isFull" to false,
                        "status" to "active",
                        "updatedAt" to System.currentTimeMillis().toString()
                    )
                    database.getReference("truck_locations").child(truckId).setValue(truckData)
                    
                    // Update Local Progress (Simulation also marks visited)
                    val visited = sessionManager.getVisitedZones()
                    visited.add(target.name)
                    sessionManager.saveVisitedZones(visited)
                    updateCollectionProgressUI(target.name, target.latitude, target.longitude)

                    android.widget.Toast.makeText(this, "Teleported to ${target.name}. Geofence logic triggered!", android.widget.Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        // Initial setup for Dashboard
        val user2 = sessionManager.getUser()
        tvDriverName.text = user2?.name ?: "Pedro Santos"
        tvTruckId.text = "Truck: ${user2?.preferredTruck ?: "GT-001"}"
        
        updateTripBasicInfo()
        updateCollectionProgressUI(null)
    }

    private fun initializeViews() {
        tvDriverName = findViewById(R.id.tvDriverName)
        tvTruckId = findViewById(R.id.tvTruckId)
        tvCurrentStatus = findViewById(R.id.tvCurrentStatus)
        
        // Trip Info
        tvPlateNumber = findViewById(R.id.tv_plate_number)
        tvStartTime = findViewById(R.id.tv_start_time)
        tvEstimatedEnd = findViewById(R.id.tv_estimated_end)
        tvTotalDistance = findViewById(R.id.tv_total_distance)
        
        // Map Tab Tracking
        tvMapCurrentPurok = findViewById(R.id.tv_map_current_purok)
        tvMapTruckNumber = findViewById(R.id.tv_map_truck_number)
        tvMapPlateNumber = findViewById(R.id.tv_map_plate_number)
        tvMapStartTime = findViewById(R.id.tv_map_start_time)
        tvMapTotalDistance = findViewById(R.id.tv_map_total_distance)
        llMapSignalStrength = findViewById(R.id.ll_map_signal_strength)
        viewMapSignalIndicator = findViewById(R.id.view_map_signal_indicator)
        tvMapSignalStrength = findViewById(R.id.tv_map_signal_strength)
        tvMapGpsAccuracy = findViewById(R.id.tv_map_gps_accuracy)
        tvMapSpeed = findViewById(R.id.tv_map_speed)
        
        // GPS Status
        llSignalStrength = findViewById(R.id.ll_signal_strength)
        viewSignalIndicator = findViewById(R.id.view_signal_indicator)
        tvSignalStrength = findViewById(R.id.tv_signal_strength)
        tvGpsAccuracy = findViewById(R.id.tv_gps_accuracy)
        tvGpsLastUpdate = findViewById(R.id.tv_gps_last_update)
        
        // Progress
        tvProgressText = findViewById(R.id.tv_progress_text)
        llCollectionStops = findViewById(R.id.ll_collection_stops)
        
        layoutDashboard = findViewById(R.id.layout_dashboard)
        layoutMap = findViewById(R.id.layout_map)
        layoutSettings = findViewById(R.id.layout_settings)
        bottomNav = findViewById(R.id.bottom_navigation)

        // Settings view references
        tvSettingsProfileName = findViewById(R.id.tv_settings_profile_name)
        tvSettingsProfileContact = findViewById(R.id.tv_settings_profile_contact)
        tvSettingsProfileTruck = findViewById(R.id.tv_settings_profile_truck)
        badgeNotifications = findViewById(R.id.badge_notification_count)
        
        findViewById<View>(R.id.btn_notifications).setOnClickListener {
            showNotificationModal()
        }
        
        findViewById<View>(R.id.btn_switch_to_map).setOnClickListener {
            switchToTab(R.id.nav_map)
        }
        
        findViewById<View>(R.id.btn_logout).setOnClickListener {
            showLogoutConfirmation()
        }

        findViewById<View>(R.id.btn_settings_logout).setOnClickListener {
            showLogoutConfirmation()
        }

        findViewById<View>(R.id.btn_progress).setOnClickListener {
            findViewById<androidx.core.widget.NestedScrollView>(R.id.layout_dashboard).smoothScrollTo(0, findViewById<View>(R.id.card_collection_stops).top)
        }
    }

    private fun updateTripBasicInfo() {
        val user = sessionManager.getUser()
        val truckDetails = sessionManager.getTruckDetails()
        
        val truckNum = user?.preferredTruck ?: truckDetails["plate"] ?: "GT-001"
        val plateNum = truckDetails["plate"] ?: "ABC 1234"
        
        tvPlateNumber.text = plateNum
        tvMapTruckNumber.text = truckNum
        tvMapPlateNumber.text = plateNum
        
        val startTimeMillis = sessionManager.getTripStartTime()
        if (startTimeMillis != 0L) {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            val startDate = Date(startTimeMillis)
            tvStartTime.text = sdf.format(startDate)
            tvMapStartTime.text = sdf.format(startDate)
            
            // Est End (Start + 4 hours)
            val cal = Calendar.getInstance()
            cal.time = startDate
            cal.add(Calendar.HOUR_OF_DAY, 4)
            tvEstimatedEnd.text = sdf.format(cal.time)
        } else {
            tvStartTime.text = "--:--"
            tvEstimatedEnd.text = "--:--"
        }
    }

    private fun updateTripMetricsUI(distanceMeters: Double, status: String) {
        val km = distanceMeters / 1000.0
        val kmText = String.format(Locale.getDefault(), "%.1f km", km)
        tvTotalDistance.text = kmText
        tvMapTotalDistance.text = kmText
        
        tvCurrentStatus.text = status.uppercase()
        val color = when (status.lowercase()) {
            "active" -> "#4CAF50"
            "idle" -> "#FFA000"
            "full" -> "#F44336"
            "completed" -> "#1976D2"
            else -> "#757575"
        }
        tvCurrentStatus.setTextColor(Color.parseColor(color))
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.status_badge)?.setCardBackgroundColor(
            Color.parseColor(color).let { Color.argb(30, Color.red(it), Color.green(it), Color.blue(it)) }
        )
    }

    private fun updateGpsStatusUI(accuracy: Double) {
        tvGpsAccuracy.text = String.format(Locale.getDefault(), "±%.0f meters", accuracy)
        tvMapGpsAccuracy.text = String.format(Locale.getDefault(), "±%.0f meters", accuracy)
        
        val sdf = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
        tvGpsLastUpdate.text = sdf.format(Date())
        
        val (strength, color) = when {
            accuracy < 10 -> "Strong" to "#00BFA5"
            accuracy < 25 -> "Good" to "#4CAF50"
            else -> "Weak" to "#F44336"
        }
        
        tvSignalStrength.text = strength
        tvSignalStrength.setTextColor(Color.parseColor(color))
        viewSignalIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(color))
        llSignalStrength.backgroundTintList = android.content.res.ColorStateList.valueOf(
            Color.parseColor(color).let { Color.argb(20, Color.red(it), Color.green(it), Color.blue(it)) }
        )

        // Sync to Map Tab
        tvMapSignalStrength.text = strength
        tvMapSignalStrength.setTextColor(Color.parseColor(color))
        viewMapSignalIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(color))
        llMapSignalStrength.backgroundTintList = android.content.res.ColorStateList.valueOf(
            Color.parseColor(color).let { Color.argb(20, Color.red(it), Color.green(it), Color.blue(it)) }
        )
    }

    private fun updateCollectionProgressUI(currentZone: String?, lat: Double = 0.0, lng: Double = 0.0) {
        val visitedZones = sessionManager.getVisitedZones()
        val allZones = PurokManager.purokZones
        val totalZones = allZones.size
        tvProgressText.text = "${visitedZones.size} / $totalZones"
        
        llCollectionStops.removeAllViews()
        
        // Logic to filter nearest 3 zones if not "View All"
        val zonesToDisplay = if (isViewAllPuroks) {
            allZones
        } else if (lat != 0.0 && lng != 0.0) {
            // Sort by distance and take top 3
            allZones.sortedBy { zone ->
                val results = FloatArray(1)
                android.location.Location.distanceBetween(lat, lng, zone.latitude, zone.longitude, results)
                results[0]
            }.take(3)
        } else {
            // Default to first 3 if no location yet
            allZones.take(3)
        }
        
        // Show selected zones, marking visited ones
        zonesToDisplay.forEach { zone ->
            val isVisited = visitedZones.contains(zone.name)
            val isCurrent = zone.name == currentZone
            
            val stopView = LayoutInflater.from(this).inflate(R.layout.item_collection_stop, llCollectionStops, false)
            stopView.findViewById<TextView>(R.id.tv_stop_name).text = zone.name
            
            val icon = stopView.findViewById<ImageView>(R.id.iv_stop_status)
            if (isVisited) {
                icon.setImageResource(R.drawable.ic_check)
                icon.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                stopView.setBackgroundResource(R.drawable.bg_stop_completed)
            } else if (isCurrent) {
                icon.setImageResource(R.drawable.ic_location)
                icon.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1976D2"))
                stopView.setBackgroundResource(R.drawable.bg_stop_current)
            } else {
                icon.setImageResource(R.drawable.ic_location)
                icon.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#BDBDBD"))
                stopView.setBackgroundResource(R.drawable.bg_settings_item)
            }
            
            llCollectionStops.addView(stopView)
        }

        // Add "View All" or "Show Less" footer
        val footerView = LayoutInflater.from(this).inflate(R.layout.item_view_all_footer, llCollectionStops, false)
        val tvFooter = footerView.findViewById<TextView>(R.id.tv_view_all)
        tvFooter.text = if (isViewAllPuroks) "Show Less" else "View All Puroks (${allZones.size})"
        
        footerView.setOnClickListener {
            isViewAllPuroks = !isViewAllPuroks
            updateCollectionProgressUI(lastCurrentZone, lastLat, lastLng)
        }
        
        llCollectionStops.addView(footerView)
    }

    private fun setupSettingsTab() {
        val user = sessionManager.getUser()
        tvSettingsProfileName.text = user?.name ?: "Pedro Santos"
        tvSettingsProfileContact.text = user?.phone ?: "09191234567"
        tvSettingsProfileTruck.text = user?.preferredTruck ?: "GT-001"
    }

    private fun setupSettingsClickListeners() {
        // Route Management
        findViewById<View>(R.id.ll_settings_view_daily_routes).setOnClickListener {
            showSettingsModal(R.layout.dialog_daily_routes)
        }

        findViewById<View>(R.id.ll_settings_performance_stats).setOnClickListener {
            showSettingsModal(R.layout.dialog_performance_stats)
        }

        // Truck Information
        findViewById<View>(R.id.ll_settings_truck_details).setOnClickListener {
            showSettingsModal(R.layout.dialog_truck_details)
        }
        findViewById<View>(R.id.ll_settings_maintenance_schedule).setOnClickListener {
            showSettingsModal(R.layout.dialog_maintenance_schedule)
        }
        findViewById<View>(R.id.ll_settings_report_issue).setOnClickListener {
            showSettingsModal(R.layout.dialog_report_truck_issue)
        }

        // Notifications
        findViewById<View>(R.id.ll_settings_notification_preferences).setOnClickListener {
            showSettingsModal(R.layout.dialog_notification_preferences)
        }
        findViewById<View>(R.id.ll_settings_alert_history).setOnClickListener {
            showSettingsModal(R.layout.dialog_alert_history)
        }
    }

    private fun showSettingsModal(layoutResId: Int) {
        if (isFinishing || isDestroyed) return
        
        try {
            val dialogView = LayoutInflater.from(this).inflate(layoutResId, null)
            val alertDialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create()
            activeDialog = alertDialog

            alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            // Special logic for Report Issue
            if (layoutResId == R.layout.dialog_report_truck_issue) {
                val btnSubmit = dialogView.findViewById<Button>(R.id.btn_submit_report)
                val etType = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_issue_type)
                val etDesc = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_issue_description)
                
                btnSubmit.setOnClickListener {
                    val type = etType.text.toString()
                    val desc = etDesc.text.toString()
                    
                    if (type.isNotEmpty() && desc.isNotEmpty()) {
                        val user = sessionManager.getUser()
                        val notification = com.example.myapplication.models.SystemNotification(
                            type = "DRIVER_ISSUE",
                            title = "New Driver Issue: $type",
                            message = "${user?.name ?: "Driver"} reported an issue: $desc",
                            timestamp = System.currentTimeMillis(),
                            isRead = false,
                            relatedId = user?.userId?.toString() ?: "",
                            userId = user?.userId?.toLong()
                        )
                        
                        val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
                        com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                            .getReference("notifications").push().setValue(notification)
                            
                        com.example.myapplication.utils.CustomNotification.showTopNotification(this, "Issue report submitted to Admin", false)
                        alertDialog.dismiss()
                    } else {
                        android.widget.Toast.makeText(this, "Please fill all fields", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (layoutResId == R.layout.dialog_truck_details) {
                updateTruckDetailsUI(dialogView, alertDialog)
            } else if (layoutResId == R.layout.dialog_maintenance_schedule) {
                updateMaintenanceUI(dialogView)
            } else if (layoutResId == R.layout.dialog_performance_stats) {
                updatePerformanceStatsUI(dialogView)
            } else if (layoutResId == R.layout.dialog_daily_routes) {
                updateDailyRoutesUI(dialogView)
            } else if (layoutResId == R.layout.dialog_alert_history) {
                updateAlertHistoryUI(dialogView)
            }

            dialogView.findViewById<View>(R.id.btn_cancel)?.setOnClickListener { alertDialog.dismiss() }
            dialogView.findViewById<View>(R.id.btn_close)?.setOnClickListener {
                alertDialog.dismiss()
            }

            alertDialog.show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Module coming soon", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateTruckDetailsUI(view: View, dialog: AlertDialog) {
        val etTruckId = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_truck_id)
        val etPlate = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_plate_number)
        val etModel = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_truck_model)
        val etFuel = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_fuel_type)
        val etCapacity = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_truck_capacity)
        val btnSave = view.findViewById<Button>(R.id.btn_save_truck)

        val user = sessionManager.getUser()
        val truckDetails = sessionManager.getTruckDetails()

        etTruckId?.setText(user?.preferredTruck ?: "GT-001")
        etPlate?.setText(truckDetails["plate"])
        etModel?.setText(truckDetails["model"])
        etFuel?.setText(truckDetails["fuel"])
        etCapacity?.setText(truckDetails["capacity"])

        btnSave?.setOnClickListener {
            val truckId = etTruckId?.text.toString().trim()
            val plate = etPlate?.text.toString().trim()
            val model = etModel?.text.toString().trim()
            val fuel = etFuel?.text.toString().trim()
            val capacity = etCapacity?.text.toString().trim()

            if (truckId.isNotEmpty() && plate.isNotEmpty()) {
                sessionManager.saveTruckDetails(truckId, plate, model, fuel, capacity)
                updateTripBasicInfo() // Refresh Dashboard and Map UI
                setupSettingsTab()    // Refresh Settings UI
                
                com.example.myapplication.utils.CustomNotification.showTopNotification(this, "Truck details updated", false)
                dialog.dismiss()
            } else {
                android.widget.Toast.makeText(this, "Truck ID and Plate are required", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateMaintenanceUI(view: View) {
        val currentMileage = sessionManager.getTruckMileage()
        
        val tvOilStatus = view.findViewById<TextView>(R.id.tv_oil_change_status)
        val tvTireStatus = view.findViewById<TextView>(R.id.tv_tire_rotation_status)
        val tvInspectionStatus = view.findViewById<TextView>(R.id.tv_inspection_status)
        
        val oilThreshold = 5000f
        val tireThreshold = 10000f
        val inspectionThreshold = 20000f
        
        tvOilStatus.text = if (currentMileage >= oilThreshold) "MAINTENANCE REQUIRED" else "Remaining: ${String.format("%.1f", oilThreshold - currentMileage)} km"
        tvTireStatus.text = if (currentMileage >= tireThreshold) "MAINTENANCE REQUIRED" else "Remaining: ${String.format("%.1f", tireThreshold - currentMileage)} km"
        tvInspectionStatus.text = if (currentMileage >= inspectionThreshold) "MAINTENANCE REQUIRED" else "Remaining: ${String.format("%.1f", inspectionThreshold - currentMileage)} km"
        
        if (currentMileage >= oilThreshold) tvOilStatus.setTextColor(Color.RED)
        if (currentMileage >= tireThreshold) tvTireStatus.setTextColor(Color.RED)
        if (currentMileage >= inspectionThreshold) tvInspectionStatus.setTextColor(Color.RED)
    }

    private fun updatePerformanceStatsUI(view: View) {
        val visitedZones = sessionManager.getVisitedZones().size
        val totalZones = PurokManager.purokZones.size
        val efficiency = if (totalZones > 0) (visitedZones.toFloat() / totalZones * 100).toInt() else 0
        
        val distanceMeters = sessionManager.getTripDistance()
        val distanceKm = distanceMeters / 1000f
        
        val startTime = sessionManager.getTripStartTime()
        val durationHours = if (startTime > 0) (System.currentTimeMillis() - startTime) / (1000f * 60 * 60) else 0f
        val avgSpeed = if (durationHours > 0.1) distanceKm / durationHours else 0f

        view.findViewById<TextView>(R.id.tv_efficiency_score).text = "$efficiency%"
        view.findViewById<TextView>(R.id.tv_total_distance_covered).text = String.format("%.1f km", distanceKm)
        view.findViewById<TextView>(R.id.tv_avg_speed).text = String.format("%.1f km/h", avgSpeed)
    }

    private fun updateDailyRoutesUI(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.ll_dynamic_routes_container)
        container.removeAllViews()
        
        val visitedZones = sessionManager.getVisitedZones()
        
        PurokManager.purokZones.forEach { zone ->
            val routeView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, container, false)
            val textView = routeView.findViewById<TextView>(android.R.id.text1)
            textView.text = zone.name
            textView.textSize = 16f
            textView.setPadding(0, 20, 0, 20)
            
            if (visitedZones.contains(zone.name)) {
                textView.setTextColor(Color.GRAY)
                textView.text = "${zone.name} (Visited)"
            } else {
                textView.setTextColor(Color.BLACK)
            }
            
            container.addView(routeView)
            
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(Color.parseColor("#E0E0E0"))
            }
            container.addView(divider)
        }
    }

    private fun updateAlertHistoryUI(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.ll_alert_history_container)
        container.removeAllViews()
        
        // This would ideally pull from a local cache or real-time DB
        // For now, we'll show a placeholder that can be dynamically populated
        val database = com.google.firebase.database.FirebaseDatabase.getInstance("https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app")
        database.getReference("complaints").limitToLast(10).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                snapshot.children.reversed().forEach { child ->
                    val zone = child.child("purok").getValue(String::class.java) ?: "Unknown"
                    val category = child.child("category").getValue(String::class.java) ?: "Other"
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    
                    val alertView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_2, container, false)
                    val tvTitle = alertView.findViewById<TextView>(android.R.id.text1)
                    val tvSub = alertView.findViewById<TextView>(android.R.id.text2)
                    
                    tvTitle.text = "Complaint in $zone"
                    tvTitle.setTextColor(Color.BLACK)
                    tvTitle.setTypeface(null, android.graphics.Typeface.BOLD)
                    
                    val date = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
                    tvSub.text = "$category reported at $date"
                    tvSub.setTextColor(Color.GRAY)
                    
                    container.addView(alertView)
                }
            } else {
                val emptyTv = TextView(this).apply {
                    text = "No recent complaints found."
                    setPadding(0, 40, 0, 40)
                    gravity = android.view.Gravity.CENTER
                }
                container.addView(emptyTv)
            }
        }
    }

    private fun setupNavigation() {
        bottomNav.selectedItemId = R.id.nav_dashboard
        bottomNav.setOnItemSelectedListener { item ->
            switchToTab(item.itemId)
            true
        }
    }

    private fun switchToTab(itemId: Int) {
        layoutDashboard.visibility = if (itemId == R.id.nav_dashboard) View.VISIBLE else View.GONE
        layoutMap.visibility = if (itemId == R.id.nav_map) View.VISIBLE else View.GONE
        layoutSettings.visibility = if (itemId == R.id.nav_settings) View.VISIBLE else View.GONE
        
        bottomNav.menu.findItem(itemId).isChecked = true
        
        if (itemId == R.id.nav_map) {
            setupMap(isFullMode = true)
        } else if (itemId == R.id.nav_dashboard) {
            setupMap(isFullMode = false)
        }
    }

    override fun onTrucksUpdated(trucks: List<com.example.myapplication.models.TruckLocation>) {
        // Implementation for driver dashboard if needed
    }

    override fun onUserLocationUpdated(location: android.location.Location) {
        // Implementation for driver dashboard if needed
    }

    private fun setupMap(isFullMode: Boolean) {
        val mode = if (isFullMode) MapboxFragment.MODE_FULL else MapboxFragment.MODE_DASHBOARD
        
        // Find the container ID
        val containerId = if (isFullMode) {
            R.id.map_fragment_container_full
        } else {
            R.id.map_fragment_container
        }

        // Check if mapFragment already exists
        if (mapFragment == null) {
            mapFragment = MapboxFragment.newInstance(mode)
            supportFragmentManager.beginTransaction()
                .replace(containerId, mapFragment!!)
                .commit()
        } else {
            // Remove from old parent and add to new parent
            val currentFragment = mapFragment!!
            supportFragmentManager.beginTransaction().remove(currentFragment).commitNow()
            
            // Re-create to ensure mode is applied (or you could add a setMode method to Fragment)
            mapFragment = MapboxFragment.newInstance(mode)
            supportFragmentManager.beginTransaction()
                .replace(containerId, mapFragment!!)
                .commit()
        }
        mapFragment?.setOnTrucksUpdatedListener(this)
    }

    private fun setupStatusControls() {
        val user = sessionManager.getUser()
        val truckId = user?.preferredTruck ?: "GT-001"
        val database = com.google.firebase.database.FirebaseDatabase.getInstance("https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app")

        findViewById<View>(R.id.btn_start).setOnClickListener {
            checkLocationPermissions {
                startGpsService("active")
                
                database.getReference("truck_locations").child(truckId).child("status").setValue("active")
                database.getReference("truck_locations").child(truckId).child("isFull").setValue(false)
            }
        }
        findViewById<View>(R.id.btn_pause).setOnClickListener {
            val serviceIntent = Intent(this, LocationUpdateService::class.java).apply {
                putExtra("status", "idle")
            }
            startService(serviceIntent)
            
            database.getReference("truck_locations").child(truckId).child("status").setValue("idle")
        }
        findViewById<View>(R.id.btn_full).setOnClickListener {
            val serviceIntent = Intent(this, LocationUpdateService::class.java).apply {
                putExtra("status", "full")
            }
            startService(serviceIntent)
            
            database.getReference("truck_locations").child(truckId).child("isFull").setValue(true)
            database.getReference("truck_locations").child(truckId).child("status").setValue("full")
            
            // Notify Admin
            val userNotify = sessionManager.getUser()
            val notification = com.example.myapplication.models.SystemNotification(
                type = "TRUCK_FULL",
                title = "Truck Full: $truckId",
                message = "${userNotify?.name ?: "Driver"} marked truck $truckId as FULL.",
                timestamp = System.currentTimeMillis(),
                isRead = false,
                relatedId = truckId
            )
            database.getReference("notifications").push().setValue(notification)

            // Log full event for analytics
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val logRef = database.getReference("collection_logs").push()
            val logData = mapOf(
                "truckId" to truckId,
                "timestamp" to System.currentTimeMillis(),
                "type" to "FULL",
                "date" to today
            )
            logRef.setValue(logData)
            
            android.widget.Toast.makeText(this, "Truck marked as FULL. Notifications stopped.", android.widget.Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btn_finish).setOnClickListener {
            database.getReference("truck_locations").child(truckId).child("status").setValue("completed")
            database.getReference("truck_locations").child(truckId).child("isFull").setValue(false)
            
            // Notify Admin
            val userNotify = sessionManager.getUser()
            val notification = com.example.myapplication.models.SystemNotification(
                type = "TRIP_COMPLETED",
                title = "Trip Completed: $truckId",
                message = "${userNotify?.name ?: "Driver"} has finished the trip for $truckId.",
                timestamp = System.currentTimeMillis(),
                isRead = false,
                relatedId = truckId
            )
            database.getReference("notifications").push().setValue(notification)

            // Stop service as the trip is done
            stopService(Intent(this, LocationUpdateService::class.java))
            
            // Clear persistent trip data for next time
            sessionManager.clearTripData()
            
            // Update UI to reflect end of trip
            updateTripBasicInfo()
            updateTripMetricsUI(0.0, "offline")
            updateCollectionProgressUI(null)
            
            android.widget.Toast.makeText(this, "Trip COMPLETED. All data finalized.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkLocationPermissions(onGranted: (() -> Unit)? = null) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1001)
        } else {
            onGranted?.invoke()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted, if they clicked start it will need another click or we can auto-start
        } else {
            android.widget.Toast.makeText(this, "Location permission is required for tracking", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLogoutConfirmation() {
        if (isFinishing || isDestroyed) return
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_logout_confirmation, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        activeDialog = alertDialog

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            alertDialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_confirm_logout).setOnClickListener {
            sessionManager.logout()
            stopService(Intent(this, LocationUpdateService::class.java))
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        alertDialog.show()
    }

    override fun onDestroy() {
        notificationListener?.let { database.getReference("notifications").removeEventListener(it) }
        try { unregisterReceiver(locationUpdateReceiver) } catch (e: Exception) {}
        activeDialog?.dismiss()
        activeDialog = null
        super.onDestroy()
    }

    private fun setupRealtimeNotifications() {
        val user = sessionManager.getUser() ?: return
        
        notificationListener = database.getReference("notifications").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                notificationList.clear()
                var unreadCount = 0
                for (child in snapshot.children) {
                    val notification = child.getValue(SystemNotification::class.java)
                    if (notification != null) {
                        // 1. Alert when Admin resolves their reported issue
                        val isMyResolvedIssue = notification.type == "DRIVER_ISSUE" && 
                                              notification.userId == user.userId.toLong() && 
                                              notification.status == "RESOLVED"
                        
                        // 2. Alert for complaints in current purok
                        val isComplaintInMyPurok = notification.type == "COMPLAINT" && 
                                                 lastCurrentZone != null && 
                                                 notification.message.contains(lastCurrentZone!!, ignoreCase = true)

                        if (isMyResolvedIssue || isComplaintInMyPurok || notification.type == "GENERAL") {
                            notificationList.add(notification.copy(id = child.key ?: ""))
                            if (!notification.isRead) unreadCount++
                        }
                    }
                }
                notificationList.sortByDescending { it.timestamp }
                
                if (unreadCount > 0) {
                    badgeNotifications.text = unreadCount.toString()
                    badgeNotifications.visibility = View.VISIBLE
                } else {
                    badgeNotifications.visibility = View.GONE
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showNotificationModal() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_notifications, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val rvNotifications = dialogView.findViewById<RecyclerView>(R.id.rv_notifications)
        val llEmpty = dialogView.findViewById<LinearLayout>(R.id.ll_empty_state)
        val btnClearAll = dialogView.findViewById<TextView>(R.id.btn_clear_all)
        val btnClose = dialogView.findViewById<View>(R.id.btn_close)

        if (notificationList.isEmpty()) {
            rvNotifications.visibility = View.GONE
            llEmpty.visibility = View.VISIBLE
        } else {
            rvNotifications.visibility = View.VISIBLE
            llEmpty.visibility = View.GONE
            
            rvNotifications.layoutManager = LinearLayoutManager(this)
            val adapter = NotificationAdapter(this, notificationList) { notification ->
                // Mark as read
                database.getReference("notifications").child(notification.id).child("isRead").setValue(true)
                dialog.dismiss()
            }
            rvNotifications.adapter = adapter

            // ✅ SWIPE TO DISMISS
            val swipeHandler = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
                override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val position = viewHolder.adapterPosition
                    val notification = notificationList[position]
                    database.getReference("notifications").child(notification.id).removeValue()
                        .addOnSuccessListener {
                            android.widget.Toast.makeText(this@DriverDashboardActivity, "Notification removed", android.widget.Toast.LENGTH_SHORT).show()
                        }
                }
            }
            androidx.recyclerview.widget.ItemTouchHelper(swipeHandler).attachToRecyclerView(rvNotifications)
        }

        btnClearAll.setOnClickListener {
            Toast.makeText(this, "Only admins can clear all notifications", Toast.LENGTH_SHORT).show()
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
