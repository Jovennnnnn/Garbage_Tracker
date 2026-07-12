package com.example.myapplication.network

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.models.ApiResponse
import com.example.myapplication.utils.PredictionEngine
import com.example.myapplication.utils.PurokManager
import com.example.myapplication.utils.SessionManager
import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

import android.media.RingtoneManager
import android.graphics.Color
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class LocationUpdateService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sessionManager: SessionManager
    private var alertListener: ValueEventListener? = null
    private val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"

    companion object {
        const val ACTION_LOCATION_UPDATE = "com.example.myapplication.LOCATION_UPDATE"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_ACCURACY = "accuracy"
        const val EXTRA_DISTANCE = "distance"
        const val EXTRA_STATUS = "status"
        const val EXTRA_CURRENT_ZONE = "current_zone"
        const val EXTRA_SPEED = "speed"
    }

    private val NOTIFICATION_ID = 12345
    private val ALERT_NOTIFICATION_ID = 54321
    private val CHANNEL_ID = "location_service_channel"
    private val ALERT_CHANNEL_ID = "garbage_alert_channel"

    private lateinit var notifiedZones: MutableSet<String>
    private lateinit var etaNotifiedZones: MutableSet<String>
    private var lastInsidePurok: String? = null
    private var lastShownAlertTimestamp: Long = 0L

    private var lastLocation: Location? = null
    private var lastSavedLocation: Location? = null
    private var MIN_DISTANCE_METERS = 5.0 // Threshold to filter out stationary jitter
    private val SMOOTHING_FACTOR = 0.5 // Lower = more smooth, but more lag
    
    private var isTruckFull = false
    private var isFullListener: ValueEventListener? = null
    private var complaintsListener: ValueEventListener? = null
    private var currentStatus = "active"
    private var serviceStartTime = 0L
    private var totalDistance = 0f
    private var zonesCoveredThisTrip = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        
        // Restore trip state
        serviceStartTime = sessionManager.getTripStartTime()
        if (serviceStartTime == 0L) {
            serviceStartTime = System.currentTimeMillis()
            sessionManager.saveTripStartTime(serviceStartTime)
        }
        
        totalDistance = sessionManager.getTripDistance()
        zonesCoveredThisTrip = sessionManager.getVisitedZones()
        
        loadNotifiedSets()
        
        val user = sessionManager.getUser()
        if (user?.role?.lowercase() == "driver") {
            val truckId = user.preferredTruck ?: "GT-001"
            isFullListener = FirebaseDatabase.getInstance(dbUrl).getReference("truck_locations").child(truckId).child("isFull").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) { isTruckFull = snapshot.getValue(Boolean::class.java) ?: false }
                override fun onCancelled(error: DatabaseError) {}
            })
            setupComplaintsListener()
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location -> lastLocation = location; updateLiveLocation(location); checkAutoFullConditions() }
            }
        }
    }

    private fun loadNotifiedSets() {
        val prefs = getSharedPreferences("location_service_prefs", Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastDate = prefs.getString("last_reset_date", "")

        if (lastDate != today) {
            notifiedZones = mutableSetOf()
            etaNotifiedZones = mutableSetOf()
            prefs.edit().putString("last_reset_date", today).apply()
            saveNotifiedSets()
        } else {
            notifiedZones = prefs.getStringSet("notified_zones", emptySet())?.toMutableSet() ?: mutableSetOf()
            etaNotifiedZones = prefs.getStringSet("eta_notified_zones", emptySet())?.toMutableSet() ?: mutableSetOf()
        }
    }

    private fun saveNotifiedSets() {
        val prefs = getSharedPreferences("location_service_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("notified_zones", notifiedZones)
            .putStringSet("eta_notified_zones", etaNotifiedZones)
            .apply()
    }

    private fun checkAutoFullConditions() {
        val user = sessionManager.getUser() ?: return
        if (user.role.lowercase() != "driver" || isTruckFull) return
        val truckId = user.preferredTruck ?: "GT-001"
        if (System.currentTimeMillis() - serviceStartTime > 4 * 60 * 60 * 1000L || zonesCoveredThisTrip.size >= 3) { markTruckAsFull(truckId, "AUTO") }
    }

    private fun setupComplaintsListener() {
        if (complaintsListener != null) return
        
        // Using "complaints" node - assuming it exists in Firebase for real-time alerts
        val ref = FirebaseDatabase.getInstance(dbUrl).getReference("complaints")
        complaintsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = sessionManager.getUser() ?: return
                if (user.role.lowercase() != "driver") return
                
                snapshot.children.forEach { child ->
                    val status = child.child("status").getValue(String::class.java) ?: "PENDING"
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    
                    // Only notify for new pending complaints
                    if (status.uppercase() == "PENDING" && timestamp > lastShownAlertTimestamp) {
                        val category = child.child("category").getValue(String::class.java) ?: "Other"
                        val zone = child.child("purok").getValue(String::class.java) ?: "Unknown"
                        
                        // Intelligent filtering: only notify if the complaint is in the driver's current zone
                        if (zone == lastInsidePurok) {
                            showSystemNotification(
                                "New Complaint in $zone",
                                "Category: $category. A resident just reported an issue in your current area.",
                                user.preferredTruck ?: "GT-001"
                            )
                            lastShownAlertTimestamp = System.currentTimeMillis()
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(complaintsListener!!)
    }

    private fun markTruckAsFull(truckId: String, reason: String) {
        isTruckFull = true
        val ref = FirebaseDatabase.getInstance(dbUrl).getReference("truck_locations").child(truckId)
        ref.child("isFull").setValue(true); ref.child("status").setValue("full")
        
        // Notify Admin
        val notification = com.example.myapplication.models.SystemNotification(
            type = "TRUCK_FULL",
            title = "Truck Full: $truckId",
            message = "Truck $truckId has been automatically marked as FULL ($reason).",
            timestamp = System.currentTimeMillis(),
            isRead = false,
            relatedId = truckId
        )
        FirebaseDatabase.getInstance(dbUrl).getReference("notifications").push().setValue(notification)

        // Notify current zone that truck is full
        lastInsidePurok?.let { zoneName ->
            FirebaseDatabase.getInstance(dbUrl).getReference("alerts").child(zoneName).setValue(mapOf(
                "message" to "Truck is full. Collection for $zoneName will resume after unloading.",
                "type" to "FULL_ALERT",
                "timestamp" to System.currentTimeMillis(),
                "truck_id" to truckId
            ))
        }

        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        FirebaseDatabase.getInstance(dbUrl).getReference("collection_logs").push().setValue(mapOf("truckId" to truckId, "timestamp" to System.currentTimeMillis(), "type" to "FULL", "reason" to reason, "date" to today))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val status = intent?.getStringExtra("status")
        if (status != null) {
            currentStatus = status
        }

        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(NOTIFICATION_ID, notification)
        startLocationUpdates()
        setupAlertListener()
        return START_STICKY
    }

    private fun setupAlertListener() {
        if (alertListener != null) return // Already attached
        
        val user = sessionManager.getUser() ?: return
        if (user.role.lowercase() != "resident") return
        val alertRef = FirebaseDatabase.getInstance(dbUrl).getReference("alerts").child(user.purok ?: "Purok 2")
        alertListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                val message = snapshot.child("message").getValue(String::class.java)
                val truckId = snapshot.child("truck_id").getValue(String::class.java) ?: ""
                
                if (timestamp > lastShownAlertTimestamp && System.currentTimeMillis() - timestamp < 600000 && message != null) {
                    lastShownAlertTimestamp = timestamp
                    
                    // Respect notification preferences
                    if (sessionManager.isAppNotificationsEnabled()) {
                        showSystemNotification("Garbage Truck Alert", message, truckId)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        alertRef.addValueEventListener(alertListener!!)
    }

    private fun showSystemNotification(title: String, message: String, truckId: String = "") {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("truck_id", truckId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 
            System.currentTimeMillis().toInt(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_truck)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { stopSelf(); return }
        fusedLocationClient.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500).build(), locationCallback, Looper.getMainLooper())
    }

    private fun updateLiveLocation(location: Location) {
        val user = sessionManager.getUser() ?: return
        val timestamp = System.currentTimeMillis()

        // --- 🛡️ INTELLIGENT GPS FILTERING ---
        // 1. ACCURACY CHECK: Ignore poor GPS signals
        if (location.accuracy > 35) {
            Log.d("LocationService", "Filtered: Low accuracy (${location.accuracy}m)")
            broadcastUpdate(location, null) // Still broadcast to update signal status UI
            return
        }

        // 2. SANITY CHECK: Ignore 0,0 points or impossible jumps
        if (location.latitude == 0.0 || location.longitude == 0.0) return

        var filteredLocation = location
        
        lastSavedLocation?.let { last ->
            val distance = location.distanceTo(last)
            
            // 3. DEAD ZONE FILTER: If moved less than threshold, ignore jitter
            if (distance < MIN_DISTANCE_METERS) {
                Log.d("LocationService", "Filtered: Jitter detected (dist: $distance)")
                broadcastUpdate(location, lastInsidePurok)
                return 
            }

            // 4. JUMP FILTER: Ignore jumps > 500m in a single update (likely signal bounce)
            if (distance > 500) {
                Log.d("LocationService", "Filtered: Impossible jump ($distance m)")
                broadcastUpdate(location, lastInsidePurok)
                return
            }
            
            // 5. LOW PASS SMOOTHING: Blend with last location to remove spikes
            val smoothedLat = (location.latitude * SMOOTHING_FACTOR) + (last.latitude * (1.0 - SMOOTHING_FACTOR))
            val smoothedLng = (location.longitude * SMOOTHING_FACTOR) + (last.longitude * (1.0 - SMOOTHING_FACTOR))
            
            filteredLocation = Location(location).apply {
                latitude = smoothedLat
                longitude = smoothedLng
            }

            // Accumulate distance
            totalDistance += distance
            sessionManager.saveTripDistance(totalDistance)

            // Update persistent truck mileage (Phase 1)
            val currentMileage = sessionManager.getTruckMileage()
            val newMileage = currentMileage + (distance / 1000f) // convert to km
            sessionManager.saveTruckMileage(newMileage)
        }
        
        lastSavedLocation = filteredLocation
        // --- END FILTERING ---

        if (user.role.lowercase() == "driver") {
            val truckId = user.preferredTruck ?: "GT-001"
            val database = FirebaseDatabase.getInstance(dbUrl).getReference("truck_locations")
            
            val effectiveStatus = if (isTruckFull) "full" else currentStatus

            database.child(truckId).updateChildren(mapOf(
                "truckId" to truckId, 
                "driverId" to user.userId, 
                "driverName" to user.name, 
                "latitude" to filteredLocation.latitude, 
                "longitude" to filteredLocation.longitude, 
                "speed" to filteredLocation.speed.toDouble(), 
                "isFull" to isTruckFull, 
                "status" to effectiveStatus, 
                "updatedAt" to timestamp
            ))
            
            if (!isTruckFull && effectiveStatus == "active") {
                checkGeofences(filteredLocation, user.preferredTruck ?: "Unknown Truck")
            }
            
            database.child(truckId).child("route_history").push().setValue(mapOf(
                "lat" to filteredLocation.latitude, 
                "lng" to filteredLocation.longitude, 
                "speed" to filteredLocation.speed.toDouble(),
                "timestamp" to timestamp
            ))

            RetrofitClient.instance.updateLocation(
                user.userId, 
                filteredLocation.latitude, 
                filteredLocation.longitude, 
                truckId, 
                filteredLocation.speed.toDouble(), 
                effectiveStatus,
                isTruckFull
            ).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {}
                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {}
            })
        } else {
            FirebaseDatabase.getInstance(dbUrl).getReference("resident_locations").child(user.userId.toString()).setValue(mapOf(
                "userId" to user.userId, 
                "name" to user.name, 
                "latitude" to filteredLocation.latitude, 
                "longitude" to filteredLocation.longitude, 
                "purok" to (user.purok ?: "Unknown"), 
                "updatedAt" to timestamp
            ))
        }

        broadcastUpdate(filteredLocation, lastInsidePurok)
    }

    private fun broadcastUpdate(location: Location, currentZone: String?) {
        val intent = Intent(ACTION_LOCATION_UPDATE)
        intent.putExtra(EXTRA_LATITUDE, location.latitude)
        intent.putExtra(EXTRA_LONGITUDE, location.longitude)
        intent.putExtra(EXTRA_ACCURACY, location.accuracy.toDouble())
        intent.putExtra(EXTRA_DISTANCE, totalDistance.toDouble())
        intent.putExtra(EXTRA_STATUS, if (isTruckFull) "full" else currentStatus)
        intent.putExtra(EXTRA_CURRENT_ZONE, currentZone)
        intent.putExtra(EXTRA_SPEED, location.speed.toDouble())
        sendBroadcast(intent)
    }

    private fun checkGeofences(location: Location, driverName: String) {
        val truckId = sessionManager.getUser()?.preferredTruck ?: "GT-001"
        
        val currentZone = PurokManager.getZoneAt(location.latitude, location.longitude)
        val currentlyInside = currentZone?.name
        
        if (currentlyInside != null) {
            // Re-fetch latest visited zones from session manager to sync with manual updates
            zonesCoveredThisTrip = sessionManager.getVisitedZones()

            if (!zonesCoveredThisTrip.contains(currentlyInside)) {
                zonesCoveredThisTrip.add(currentlyInside)
                sessionManager.saveVisitedZones(zonesCoveredThisTrip)
            }

            if (currentlyInside != lastInsidePurok) {
                logZoneEvent(truckId, currentlyInside, "ENTRY")
                if (!isTruckFull && !notifiedZones.contains(currentlyInside)) {
                    sendPurokAlert(currentlyInside, "The garbage truck has arrived at $currentlyInside. Please bring out your trash!", driverName, truckId, location)
                    notifiedZones.add(currentlyInside)
                    saveNotifiedSets()
                }
            }
        }
        
        if (lastInsidePurok != null && lastInsidePurok != currentlyInside) {
            logZoneEvent(truckId, lastInsidePurok!!, "EXIT")
        }
        lastInsidePurok = currentlyInside

        if (!isTruckFull) {
            for (zone in PurokManager.purokZones) {
                if (zone.name == currentlyInside) continue
                if (etaNotifiedZones.contains(zone.name)) continue
                
                val zoneLoc = Location("").apply { latitude = zone.latitude; longitude = zone.longitude }
                val distance = location.distanceTo(zoneLoc)
                
                if (distance < 5000) {
                    val etaSeconds = PredictionEngine.predictArrivalTime(distance.toDouble(), emptyList())
                    if (etaSeconds <= 900) {
                        if (location.hasBearing()) {
                            val bearingToZone = location.bearingTo(zoneLoc)
                            val bearingDiff = Math.abs(location.bearing - bearingToZone)
                            if (bearingDiff > 90 && bearingDiff < 270) continue
                        }

                        sendPurokAlert(zone.name, "Garbage truck is about 15 minutes away from ${zone.name}. Please prepare your trash!", driverName, truckId, location)
                        etaNotifiedZones.add(zone.name)
                        saveNotifiedSets()
                    }
                }
            }
        }
    }

    private fun sendPurokAlert(purokName: String, message: String, driver: String, truckId: String, loc: Location) {
        val alertData = mapOf(
            "message" to message,
            "timestamp" to System.currentTimeMillis(),
            "driver" to driver,
            "truck_id" to truckId,
            "latitude" to loc.latitude,
            "longitude" to loc.longitude,
            "type" to if (message.contains("15 minutes")) "PREP_ALERT" else "ARRIVAL_ALERT"
        )
        FirebaseDatabase.getInstance(dbUrl).getReference("alerts").child(purokName).setValue(alertData)
    }

    private fun logZoneEvent(truckId: String, zoneName: String, type: String) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        FirebaseDatabase.getInstance(dbUrl).getReference("collection_logs").push().setValue(mapOf("truckId" to truckId, "zoneName" to zoneName, "timestamp" to System.currentTimeMillis(), "type" to type, "date" to today))
        RetrofitClient.instance.logCollection(truckId, zoneName, type).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {}
            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {}
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "System Service", NotificationManager.IMPORTANCE_MIN))
            manager.createNotificationChannel(NotificationChannel(ALERT_CHANNEL_ID, "Garbage Alerts", NotificationManager.IMPORTANCE_HIGH).apply { enableLights(true); lightColor = Color.GREEN; enableVibration(true) })
        }
    }

    private fun createNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Waste Management").setContentText("Service is active").setSmallIcon(R.drawable.ic_truck).setPriority(NotificationCompat.PRIORITY_MIN).build()
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { 
        fusedLocationClient.removeLocationUpdates(locationCallback)
        alertListener?.let {
            val user = sessionManager.getUser()
            val purok = user?.purok ?: "Purok 2"
            FirebaseDatabase.getInstance(dbUrl).getReference("alerts").child(purok).removeEventListener(it)
        }
        isFullListener?.let {
            val user = sessionManager.getUser()
            val truckId = user?.preferredTruck ?: "GT-001"
            FirebaseDatabase.getInstance(dbUrl).getReference("truck_locations").child(truckId).child("isFull").removeEventListener(it)
        }
        complaintsListener?.let {
            FirebaseDatabase.getInstance(dbUrl).getReference("complaints").removeEventListener(it)
        }
        super.onDestroy() 
    }
}
