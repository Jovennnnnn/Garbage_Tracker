package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
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
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.widget.Toast
import com.google.firebase.database.*
import com.example.myapplication.models.SystemNotification
import com.example.myapplication.adapters.NotificationAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DriverDashboardActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var tvDriverName: TextView
    private lateinit var tvTruckId: TextView
    private lateinit var tvCurrentStatus: TextView
    
    private lateinit var layoutDashboard: android.view.View
    private lateinit var layoutMap: android.view.View
    private lateinit var layoutSettings: android.view.View
    private lateinit var bottomNav: BottomNavigationView

    // Settings tab views
    private lateinit var tvSettingsProfileName: TextView
    private lateinit var tvSettingsProfileContact: TextView
    private lateinit var tvSettingsProfileTruck: TextView
    
    private var mapFragment: MapboxFragment? = null
    private var activeDialog: AlertDialog? = null

    // Notifications
    private val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
    private val database = FirebaseDatabase.getInstance(dbUrl)
    private var notificationListener: ValueEventListener? = null
    private val notificationList = mutableListOf<SystemNotification>()
    private lateinit var badgeNotifications: TextView
    private var lastCurrentZone: String? = null

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
        setupMap(isFullMode = false)
        setupRealtimeNotifications()
        checkLocationPermissions()
        
        // Initial setup for Dashboard
        val user = sessionManager.getUser()
        tvDriverName.text = user?.name ?: "Pedro Santos"
        tvTruckId.text = "Truck: ${user?.preferredTruck ?: "GT-001"}"
    }

    private fun initializeViews() {
        tvDriverName = findViewById(R.id.tvDriverName)
        tvTruckId = findViewById(R.id.tvTruckId)
        tvCurrentStatus = findViewById(R.id.tvCurrentStatus)
        
        layoutDashboard = findViewById(R.id.layout_dashboard)
        layoutMap = findViewById(R.id.layout_map)
        layoutSettings = findViewById(R.id.layout_settings)
        bottomNav = findViewById(R.id.bottom_navigation)

        // Settings view references
        tvSettingsProfileName = findViewById(R.id.tv_settings_profile_name)
        tvSettingsProfileContact = findViewById(R.id.tv_settings_profile_contact)
        tvSettingsProfileTruck = findViewById(R.id.tv_settings_profile_truck)
        badgeNotifications = findViewById(R.id.badge_notification_count)

        findViewById<android.view.View>(R.id.btn_notifications).setOnClickListener {
            showNotificationModal()
        }

        findViewById<android.view.View>(R.id.btn_switch_to_map).setOnClickListener {
            switchToTab(R.id.nav_map)
        }
        
        findViewById<android.view.View>(R.id.btn_logout).setOnClickListener {
            showLogoutConfirmation()
        }

        findViewById<android.view.View>(R.id.btn_settings_logout).setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun setupSettingsTab() {
        val user = sessionManager.getUser()
        tvSettingsProfileName.text = user?.name ?: "Pedro Santos"
        tvSettingsProfileContact.text = user?.phone ?: "09191234567"
        tvSettingsProfileTruck.text = user?.preferredTruck ?: "GT-001"
    }

    private fun setupSettingsClickListeners() {
        // Route Management
        findViewById<android.view.View>(R.id.ll_settings_view_daily_routes).setOnClickListener {
            showSettingsModal(R.layout.dialog_daily_routes)
        }

        findViewById<android.view.View>(R.id.ll_settings_performance_stats).setOnClickListener {
            showSettingsModal(R.layout.dialog_performance_stats)
        }

        // Truck Information
        findViewById<android.view.View>(R.id.ll_settings_truck_details).setOnClickListener {
            showSettingsModal(R.layout.dialog_truck_details)
        }
        findViewById<android.view.View>(R.id.ll_settings_maintenance_schedule).setOnClickListener {
            showSettingsModal(R.layout.dialog_maintenance_schedule)
        }
        findViewById<android.view.View>(R.id.ll_settings_report_issue).setOnClickListener {
            showSettingsModal(R.layout.dialog_report_truck_issue)
        }

        // Notifications
        findViewById<android.view.View>(R.id.ll_settings_notification_preferences).setOnClickListener {
            showSettingsModal(R.layout.dialog_notification_preferences)
        }
        findViewById<android.view.View>(R.id.ll_settings_alert_history).setOnClickListener {
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

            dialogView.findViewById<android.view.View>(R.id.btn_close)?.setOnClickListener {
                alertDialog.dismiss()
            }

            alertDialog.show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Module coming soon", android.widget.Toast.LENGTH_SHORT).show()
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
        layoutDashboard.visibility = if (itemId == R.id.nav_dashboard) android.view.View.VISIBLE else android.view.View.GONE
        layoutMap.visibility = if (itemId == R.id.nav_map) android.view.View.VISIBLE else android.view.View.GONE
        layoutSettings.visibility = if (itemId == R.id.nav_settings) android.view.View.VISIBLE else android.view.View.GONE
        
        bottomNav.menu.findItem(itemId).isChecked = true
        
        if (itemId == R.id.nav_map) {
            setupMap(isFullMode = true)
        } else if (itemId == R.id.nav_dashboard) {
            setupMap(isFullMode = false)
        }
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
    }

    private fun setupStatusControls() {
        findViewById<android.view.View>(R.id.btn_start).setOnClickListener {
            checkLocationPermissions {
                tvCurrentStatus.text = "ACTIVE"
                tvCurrentStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                startService(Intent(this, LocationUpdateService::class.java))
            }
        }
        findViewById<android.view.View>(R.id.btn_pause).setOnClickListener {
            tvCurrentStatus.text = "PAUSED"
            tvCurrentStatus.setTextColor(android.graphics.Color.parseColor("#FFA000"))
            stopService(Intent(this, LocationUpdateService::class.java))
        }
        findViewById<android.view.View>(R.id.btn_finish).setOnClickListener {
            tvCurrentStatus.text = "COMPLETED"
            tvCurrentStatus.setTextColor(android.graphics.Color.parseColor("#1976D2"))
            stopService(Intent(this, LocationUpdateService::class.java))
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
        val btnClose = dialogView.findViewById<android.view.View>(R.id.btn_close)

        if (notificationList.isEmpty()) {
            rvNotifications.visibility = android.view.View.GONE
            llEmpty.visibility = android.view.View.VISIBLE
        } else {
            rvNotifications.visibility = android.view.View.VISIBLE
            llEmpty.visibility = android.view.View.GONE
            
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
