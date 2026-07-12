package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.fragments.MapboxFragment
import com.example.myapplication.network.LocationUpdateService
import com.example.myapplication.utils.SessionManager
import com.example.myapplication.utils.GpsStatusMonitor
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.os.Build
import android.widget.LinearLayout
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import android.widget.Toast
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView

class ResidentDashboardActivity : AppCompatActivity(), MapboxFragment.OnTrucksUpdatedListener {

    private lateinit var sessionManager: SessionManager
    private lateinit var tvWelcome: TextView
    private lateinit var tvUserPurok: TextView
    private lateinit var tvActiveTrucksCount: TextView
    private lateinit var tvEstimatedTime: TextView
    private lateinit var tvFrequency: TextView
    private lateinit var tvTimeWindow: TextView
    private lateinit var tvSchedulePurok: TextView
    private lateinit var badgeNotifications: TextView
    private lateinit var cardNearbyAlert: MaterialCardView
    private lateinit var tvNearbyTitle: TextView
    private lateinit var tvNearbyMessage: TextView

    private var mapFragment: MapboxFragment? = null
    private var isGpsActive: Boolean = true

    private var logoutDialog: AlertDialog? = null
    private var lastAlertTimestamp: Long = 0L
    private var lastDismissedTimestamp: Long = 0L
    private var autoDismissHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val dismissRunnable = Runnable {
        animateOutAndHide(cardNearbyAlert)
    }

    private val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
    private val database = FirebaseDatabase.getInstance(dbUrl)
    private var notificationListener: ValueEventListener? = null
    private var arrivalAlertListener: ValueEventListener? = null
    private val notificationList = mutableListOf<com.example.myapplication.models.SystemNotification>()

    private var lastUserLocation: android.location.Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Continuous GPS Monitoring
        lifecycle.addObserver(GpsStatusMonitor(this) { isEnabled ->
            isGpsActive = isEnabled
            if (!isEnabled) {
                mapFragment?.clearMap() // Pause map visuals
            }
        })

        enableEdgeToEdge()
        setContentView(R.layout.activity_resident_dashboard)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.resident_dashboard_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sessionManager = SessionManager(this)
        lastDismissedTimestamp = getSharedPreferences("dashboard_prefs", MODE_PRIVATE).getLong("last_dismissed_alert", 0L)
        
        tvWelcome = findViewById(R.id.tvWelcome)
        tvUserPurok = findViewById(R.id.tvUserPurok)
        tvActiveTrucksCount = findViewById(R.id.tvActiveTrucksCount)
        tvEstimatedTime = findViewById(R.id.tvEstimatedTime)
        tvFrequency = findViewById(R.id.tvFrequency)
        tvTimeWindow = findViewById(R.id.tvTimeWindow)
        tvSchedulePurok = findViewById(R.id.tvSchedulePurok)
        badgeNotifications = findViewById(R.id.badge_notification_count)
        cardNearbyAlert = findViewById(R.id.cardNearbyAlert)
        tvNearbyTitle = findViewById(R.id.tvNearbyTitle)
        tvNearbyMessage = findViewById(R.id.tvNearbyMessage)

        val user = sessionManager.getUser()
        tvWelcome.text = user?.name ?: "Juan Dela Cruz"
        tvUserPurok.text = user?.purok ?: "Purok 2"

        mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment_container) as? MapboxFragment
        if (mapFragment == null) {
            mapFragment = MapboxFragment.newInstance(MapboxFragment.MODE_DASHBOARD)
            supportFragmentManager.beginTransaction()
                .replace(R.id.map_fragment_container, mapFragment!!)
                .commit()
        }
        mapFragment?.setOnTrucksUpdatedListener(this)

        setupClickListeners()
        setupTestTrigger()
        setupBottomNavigation()
        setupLogout()
        checkLocationPermissions()
        setupCollectionSchedule()

        // Check for deep link from MainActivity
        val truckIdFromIntent = intent.getStringExtra("truck_id")
        if (truckIdFromIntent != null) {
            val intent = Intent(this, TrackTrucksActivity::class.java)
            intent.putExtra("truck_id", truckIdFromIntent)
            startActivity(intent)
        }
    }

    private fun checkLocationPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 1001)
        } else {
            startLocationService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationService()
        }
    }

    private fun startLocationService() {
        startService(Intent(this, LocationUpdateService::class.java))
    }

    override fun onResume() {
        super.onResume()
        setupRealtimeNotifications()
    }

    override fun onPause() {
        super.onPause()
        removeRealtimeListeners()
    }

    private fun setupRealtimeNotifications() {
        val user = sessionManager.getUser() ?: return
        val userPurok = user.purok ?: ""

        // 1. System Notifications Listener (Complaints, etc)
        notificationListener = database.getReference("notifications").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                notificationList.clear()
                var unreadCount = 0
                for (child in snapshot.children) {
                    val notification = child.getValue(com.example.myapplication.models.SystemNotification::class.java)
                    if (notification != null) {
                        // Filter for this user's notifications (e.g. complaint resolved)
                        // Note: Backend should set userId in notification
                        if (notification.userId == user.userId.toLong() || notification.type == "GENERAL" || notification.type == "ARRIVAL_ALERT") {
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

        // 2. Arrival Alerts Listener (Truck Proximity)
        if (userPurok.isNotEmpty()) {
            arrivalAlertListener = database.getReference("alerts").child(userPurok).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val message = snapshot.child("message").getValue(String::class.java) ?: ""
                        val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                        val truckId = snapshot.child("truck_id").getValue(String::class.java) ?: ""
                        
                        // Only show if alert is recent (less than 1 hour old)
                        if (System.currentTimeMillis() - timestamp < 3600000) {
                            tvNearbyMessage.text = message
                            
                            // 1. Is this a newer alert than the one we dismissed?
                            if (timestamp > lastDismissedTimestamp) {
                                // 2. Is this a NEW alert we haven't shown in THIS activity instance session?
                                if (timestamp > lastAlertTimestamp) {
                                    lastAlertTimestamp = timestamp
                                    
                                    // CHECK NOTIFICATION PREFERENCE BEFORE SHOWING
                                    if (sessionManager.isAppNotificationsEnabled()) {
                                        showAnimatedAlert(truckId)
                                    }
                                    saveAlertToNotifications(message, truckId, timestamp)
                                } else {
                                    // It's the current alert, and it's not dismissed yet
                                    if (sessionManager.isAppNotificationsEnabled()) {
                                        cardNearbyAlert.visibility = View.VISIBLE
                                    } else {
                                        cardNearbyAlert.visibility = View.GONE
                                    }
                                }
                            } else {
                                // This specific alert was already dismissed by the user
                                cardNearbyAlert.visibility = View.GONE
                            }
                        } else {
                            cardNearbyAlert.visibility = View.GONE
                        }
                    } else {
                        cardNearbyAlert.visibility = View.GONE
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    private fun saveAlertToNotifications(message: String, truckId: String, timestamp: Long) {
        val user = sessionManager.getUser() ?: return
        
        // Check if we already have this exact alert recently to avoid duplicates
        val recentThreshold = 5 * 60 * 1000 // 5 minutes
        val isDuplicate = notificationList.any { 
            it.type == "ARRIVAL_ALERT" && 
            it.relatedId == truckId && 
            Math.abs(it.timestamp - timestamp) < recentThreshold 
        }
        
        if (isDuplicate) return

        val notificationData = mapOf(
            "type" to "ARRIVAL_ALERT",
            "title" to "Truck Nearby!",
            "message" to message,
            "userId" to user.userId,
            "timestamp" to timestamp,
            "isRead" to false,
            "relatedId" to truckId
        )
        database.getReference("notifications").push().setValue(notificationData)
    }

    private fun showAnimatedAlert(truckId: String) {
        // Cancel any pending dismissal
        autoDismissHandler.removeCallbacks(dismissRunnable)
        
        // Show with animation and layout transition
        val parent = findViewById<android.view.ViewGroup>(R.id.welcome_container).parent as? android.view.ViewGroup
        if (parent != null) {
            android.transition.TransitionManager.beginDelayedTransition(parent)
        }
        
        cardNearbyAlert.visibility = View.VISIBLE
        cardNearbyAlert.alpha = 0f
        cardNearbyAlert.translationY = -50f
        cardNearbyAlert.scaleX = 0.9f
        cardNearbyAlert.scaleY = 0.9f
        
        cardNearbyAlert.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .setInterpolator(android.view.animation.OvershootInterpolator())
            .start()

        // Set click listener with truckId
        cardNearbyAlert.setOnClickListener {
            // Dismiss immediately on click
            animateOutAndHide(cardNearbyAlert)
            
            val intent = Intent(this, TrackTrucksActivity::class.java)
            intent.putExtra("truck_id", truckId)
            startActivity(intent)
        }

        // Auto dismiss after 7 seconds
        autoDismissHandler.postDelayed(dismissRunnable, 7000)
    }

    private fun animateOutAndHide(view: View) {
        lastDismissedTimestamp = lastAlertTimestamp
        getSharedPreferences("dashboard_prefs", MODE_PRIVATE).edit().putLong("last_dismissed_alert", lastDismissedTimestamp).apply()

        val parent = findViewById<android.view.ViewGroup>(R.id.welcome_container).parent as? android.view.ViewGroup
        
        view.animate()
            .alpha(0f)
            .translationY(-30f)
            .setDuration(400)
            .withEndAction { 
                if (parent != null) {
                    android.transition.TransitionManager.beginDelayedTransition(parent)
                }
                view.visibility = View.GONE 
            }
            .start()
    }

    private fun removeRealtimeListeners() {
        notificationListener?.let { database.getReference("notifications").removeEventListener(it) }
        arrivalAlertListener?.let { 
            val userPurok = sessionManager.getUser()?.purok ?: ""
            if (userPurok.isNotEmpty()) {
                database.getReference("alerts").child(userPurok).removeEventListener(it)
            }
        }
    }

    private fun setupTestTrigger() {
        findViewById<MaterialCardView>(R.id.cardNearbyAlert).setOnClickListener {
            // Check Notification Permission first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Please allow notifications in settings", Toast.LENGTH_LONG).show()
                    val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    startActivity(intent)
                    return@setOnClickListener
                }
            }

            val user = sessionManager.getUser() ?: return@setOnClickListener
            val purok = user.purok ?: "Purok 2"
            val driverName = "John Driver"
            val truckId = "GT-777"
            val lat = 13.9402
            val lng = 121.1638
            
            val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
            val db = FirebaseDatabase.getInstance(dbUrl)
            
            // 1. Send Alert (for Notification) - Simulating an Arrival Alert
            val alertData = mapOf(
                "message" to "The garbage truck has arrived at $purok. Please bring out your trash!",
                "timestamp" to System.currentTimeMillis(),
                "driver" to driverName,
                "truck_id" to truckId,
                "latitude" to lat,
                "longitude" to lng,
                "type" to "ARRIVAL_ALERT"
            )
            db.getReference("alerts").child(purok).setValue(alertData)
            
            // Simulating a Complaint Resolved notification
            val notificationData = mapOf(
                "type" to "ARRIVAL_ALERT",
                "title" to "Truck Nearby!",
                "message" to "The garbage truck is heading to $purok. Please prepare your trash!",
                "userId" to user.userId,
                "timestamp" to System.currentTimeMillis(),
                "isRead" to false,
                "relatedId" to truckId
            )
            db.getReference("notifications").push().setValue(notificationData)
            
            // 2. Update Map Position (for real-time map marker)
            val truckData = mapOf(
                "truckId" to truckId,
                "driverName" to driverName,
                "latitude" to lat,
                "longitude" to lng,
                "speed" to 15.5,
                "isFull" to false,
                "status" to "active",
                "updatedAt" to System.currentTimeMillis().toString()
            )
            db.getReference("truck_locations").child(truckId).setValue(truckData)
            
            Toast.makeText(this, "Simulating $truckId nearby...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupLogout() {
        findViewById<View>(R.id.btn_logout).setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun showLogoutConfirmation() {
        if (isFinishing || isDestroyed) return
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_logout_confirmation_resident, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        logoutDialog = alertDialog

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
        logoutDialog?.dismiss()
        logoutDialog = null
        super.onDestroy()
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btn_notifications).setOnClickListener {
            showNotificationModal()
        }

        findViewById<MaterialCardView>(R.id.cardTrackTruckQuick).setOnClickListener {
            startActivity(Intent(this, TrackTrucksActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardFileComplaintQuick).setOnClickListener {
            startActivity(Intent(this, ResidentComplaintsActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardRateService).setOnClickListener {
            showFeedbackDialog()
        }

        findViewById<TextView>(R.id.tvFullMap).setOnClickListener {
            startActivity(Intent(this, TrackTrucksActivity::class.java))
        }

        cardNearbyAlert.setOnClickListener {
            startActivity(Intent(this, TrackTrucksActivity::class.java))
        }
    }

    private fun showNotificationModal() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_notifications, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val rvNotifications = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_notifications)
        val llEmpty = dialogView.findViewById<LinearLayout>(R.id.ll_empty_state)
        val btnClearAll = dialogView.findViewById<TextView>(R.id.btn_clear_all)
        val btnClose = dialogView.findViewById<View>(R.id.btn_close)

        if (notificationList.isEmpty()) {
            rvNotifications.visibility = View.GONE
            llEmpty.visibility = View.VISIBLE
        } else {
            rvNotifications.visibility = View.VISIBLE
            llEmpty.visibility = View.GONE
            
            rvNotifications.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
            val adapter = com.example.myapplication.adapters.NotificationAdapter(this, notificationList) { notification ->
                // Mark as read
                database.getReference("notifications").child(notification.id).child("isRead").setValue(true)
                
                // Navigate based on type
                when (notification.type) {
                    "COMPLAINT", "COMPLAINT_RESOLVED" -> {
                        startActivity(Intent(this, ResidentComplaintsActivity::class.java))
                    }
                    "ARRIVAL_ALERT" -> {
                        val intent = Intent(this, TrackTrucksActivity::class.java)
                        intent.putExtra("truck_id", notification.relatedId)
                        startActivity(intent)
                    }
                }
                dialog.dismiss()
            }
            rvNotifications.adapter = adapter

            // ✅ SWIPE TO DISMISS
            val swipeHandler = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
                override fun onMove(rv: androidx.recyclerview.widget.RecyclerView, vh: androidx.recyclerview.widget.RecyclerView.ViewHolder, target: androidx.recyclerview.widget.RecyclerView.ViewHolder): Boolean = false
                override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
                    val position = viewHolder.adapterPosition
                    val notification = notificationList[position]
                    
                    // Remove from Firebase
                    database.getReference("notifications").child(notification.id).removeValue()
                        .addOnSuccessListener {
                            Toast.makeText(this@ResidentDashboardActivity, "Notification removed", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            androidx.recyclerview.widget.ItemTouchHelper(swipeHandler).attachToRecyclerView(rvNotifications)
        }

        btnClearAll.setOnClickListener {
            // Only clear notifications for this user
            val userId = sessionManager.getUser()?.userId ?: -1
            database.getReference("notifications").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val nUserId = child.child("userId").getValue(Long::class.java)
                        if (nUserId == userId.toLong()) {
                            child.ref.removeValue()
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
            dialog.dismiss()
            Toast.makeText(this, "Notifications cleared", Toast.LENGTH_SHORT).show()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showFeedbackDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_feedback, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val ratingBar = dialogView.findViewById<android.widget.RatingBar>(R.id.ratingBar)
        val etFeedback = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etFeedback)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmitFeedback)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)

        btnCancel.setOnClickListener { alertDialog.dismiss() }

        btnSubmit.setOnClickListener {
            val rating = ratingBar.rating
            val feedbackText = etFeedback.text.toString()
            val user = sessionManager.getUser()

            val feedbackData = mapOf(
                "userId" to (user?.userId ?: 0),
                "userName" to (user?.name ?: "Anonymous"),
                "rating" to rating,
                "feedback" to feedbackText,
                "timestamp" to System.currentTimeMillis()
            )

            val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
            FirebaseDatabase.getInstance(dbUrl).getReference("user_evaluations").push().setValue(feedbackData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
                    alertDialog.dismiss()
                }
        }

        alertDialog.show()
    }

    override fun onTrucksUpdated(trucks: List<com.example.myapplication.models.TruckLocation>) {
        val activeTrucks = trucks.filter { it.status.lowercase() == "active" }
        tvActiveTrucksCount.text = activeTrucks.size.toString()

        calculateETA(activeTrucks)
    }

    override fun onUserLocationUpdated(location: android.location.Location) {
        lastUserLocation = location
        // Optional: recalculate ETA immediately if trucks are already loaded
        mapFragment?.let { 
            // We need currentTrucks from fragment or just wait for next truck update
            // For now, it will update on next truck movement which is frequent enough
        }
    }

    private fun calculateETA(activeTrucks: List<com.example.myapplication.models.TruckLocation>) {
        if (activeTrucks.isEmpty()) {
            tvEstimatedTime.text = "--"
            return
        }

        // Get target location: either current GPS or Purok center
        val targetLat: Double
        val targetLng: Double

        if (lastUserLocation != null) {
            targetLat = lastUserLocation!!.latitude
            targetLng = lastUserLocation!!.longitude
        } else {
            val userPurok = sessionManager.getUser()?.purok ?: "Purok 2"
            val zone = mapFragment?.getPurokZones()?.find { it.name.equals(userPurok, ignoreCase = true) }
            if (zone != null) {
                targetLat = zone.latitude
                targetLng = zone.longitude
            } else {
                // Default fallback to center of Balintawak
                targetLat = 13.955805
                targetLng = 121.158888
            }
        }

        var minTimeMinutes = Double.MAX_VALUE

        for (truck in activeTrucks) {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(truck.latitude, truck.longitude, targetLat, targetLng, results)
            val distanceMeters = results[0]

            // Average speed of garbage truck in residential area ~ 15-20 km/h
            val speedKmH = if (truck.speed > 5) truck.speed else 15.0
            val speedMS = speedKmH / 3.6
            val timeSeconds = distanceMeters / speedMS
            val timeMinutes = timeSeconds / 60.0

            if (timeMinutes < minTimeMinutes) {
                minTimeMinutes = timeMinutes
            }
        }

        if (minTimeMinutes == Double.MAX_VALUE) {
            tvEstimatedTime.text = "--"
        } else if (minTimeMinutes < 1.0) {
            tvEstimatedTime.text = "Nearby"
        } else {
            tvEstimatedTime.text = "~ ${minTimeMinutes.toInt()}m"
        }
    }

    private fun setupCollectionSchedule() {
        val user = sessionManager.getUser() ?: return
        val userPurok = user.purok ?: "Purok 2"
        tvSchedulePurok.text = userPurok

        database.getReference("collection_logs").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val entryTimes = mutableListOf<Long>()
                val daysOfWeek = mutableMapOf<Int, Int>() // Calendar.DAY_OF_WEEK -> Count
                val uniqueDates = mutableSetOf<String>()
                val calendar = java.util.Calendar.getInstance()
                val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)

                for (log in snapshot.children) {
                    val zone = log.child("zoneName").getValue(String::class.java) ?: ""
                    val type = log.child("type").getValue(String::class.java) ?: ""
                    val timestamp = log.child("timestamp").getValue(Long::class.java) ?: 0L
                    val date = log.child("date").getValue(String::class.java) ?: ""

                    if (zone.equals(userPurok, ignoreCase = true) && type == "ENTRY" && timestamp > thirtyDaysAgo) {
                        entryTimes.add(timestamp)
                        uniqueDates.add(date)
                        
                        calendar.timeInMillis = timestamp
                        val day = calendar.get(java.util.Calendar.DAY_OF_WEEK)
                        daysOfWeek[day] = (daysOfWeek[day] ?: 0) + 1
                    }
                }

                if (uniqueDates.isEmpty()) {
                    tvFrequency.text = "Analyzing Data..."
                    tvTimeWindow.text = "Collecting history"
                    return
                }

                // Calculate Frequency
                val totalDaysObserved = uniqueDates.size
                if (totalDaysObserved >= 20) {
                    tvFrequency.text = "Daily"
                } else {
                    val dayNames = arrayOf("", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                    val frequentDays = daysOfWeek.filter { it.value >= totalDaysObserved / 2 }
                        .keys.sorted()
                        .map { dayNames[it] }
                    
                    if (frequentDays.isNotEmpty()) {
                        tvFrequency.text = frequentDays.joinToString(", ")
                    } else {
                        tvFrequency.text = "Weekly"
                    }
                }

                // Calculate Time Window
                if (entryTimes.isNotEmpty()) {
                    // Simple logic: find earliest and latest entry times (hours/minutes only)
                    var minMinutes = 24 * 60
                    var maxMinutes = 0
                    
                    for (time in entryTimes) {
                        calendar.timeInMillis = time
                        val minutesOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
                        if (minutesOfDay < minMinutes) minMinutes = minutesOfDay
                        if (minutesOfDay > maxMinutes) maxMinutes = minutesOfDay
                    }
                    
                    // Expand window slightly for realistic estimation
                    val startMinutes = Math.max(0, minMinutes - 30)
                    val endMinutes = Math.min(24 * 60 - 1, maxMinutes + 60)
                    
                    val startTime = formatMinutes(startMinutes)
                    val endTime = formatMinutes(endMinutes)
                    tvTimeWindow.text = "$startTime - $endTime"
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun formatMinutes(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, hours)
        calendar.set(java.util.Calendar.MINUTE, minutes)
        return java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(calendar.time)
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_home

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_track -> {
                    startActivity(Intent(this, TrackTrucksActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_complaints -> {
                    startActivity(Intent(this, ResidentComplaintsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, ResidentSettingsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}
