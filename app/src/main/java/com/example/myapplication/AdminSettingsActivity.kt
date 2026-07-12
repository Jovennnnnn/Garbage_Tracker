package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AlertDialog
import com.example.myapplication.utils.SessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.models.BackupFile
import com.example.myapplication.models.BackupHistoryResponse
import com.example.myapplication.models.UsersResponse
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.utils.DataExporter
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AdminSettingsActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_admin_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupHeader()
        setupProfile()
        setupBottomNavigation()
        setupSettingsActions()

        findViewById<MaterialButton>(R.id.btn_logout).setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun setupSettingsActions() {
        val switchEmail = findViewById<SwitchMaterial>(R.id.switch_email_notifications)
        val switchApp = findViewById<SwitchMaterial>(R.id.switch_app_notifications)
        val switchAutoBackup = findViewById<SwitchMaterial>(R.id.switch_auto_backup)

        switchEmail.isChecked = sessionManager.isEmailNotificationsEnabled()
        switchApp.isChecked = sessionManager.isAppNotificationsEnabled()
        switchAutoBackup.isChecked = sessionManager.isAutoBackupEnabled()

        switchEmail.setOnCheckedChangeListener { _, isChecked ->
            sessionManager.setEmailNotificationsEnabled(isChecked)
            Toast.makeText(this, "Email notifications ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        switchApp.setOnCheckedChangeListener { _, isChecked ->
            sessionManager.setAppNotificationsEnabled(isChecked)
            Toast.makeText(this, "App notifications ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        switchAutoBackup.setOnCheckedChangeListener { _, isChecked ->
            sessionManager.setAutoBackupEnabled(isChecked)
            Toast.makeText(this, "Auto Backup ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        findViewById<android.view.View>(R.id.row_backup_now).setOnClickListener {
            performManualBackup()
        }

        findViewById<android.view.View>(R.id.tv_view_backup_history).setOnClickListener {
            showBackupHistory()
        }

        findViewById<android.view.View>(R.id.row_export_data).setOnClickListener {
            exportDataToPDF()
        }

        findViewById<android.view.View>(R.id.row_change_password).setOnClickListener {
            showModal(R.layout.dialog_change_password)
        }
        findViewById<android.view.View>(R.id.row_two_factor).setOnClickListener {
            showModal(R.layout.dialog_two_factor)
        }
        findViewById<android.view.View>(R.id.row_access_logs).setOnClickListener {
            showModal(R.layout.dialog_access_logs)
        }
        findViewById<android.view.View>(R.id.row_user_permissions).setOnClickListener {
            showModal(R.layout.dialog_user_permissions)
        }
    }

    private fun performManualBackup() {
        val progressDialog = android.app.ProgressDialog(this)
        progressDialog.setMessage("Creating system backup...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        RetrofitClient.instance.triggerBackup().enqueue(object : Callback<com.example.myapplication.models.ApiResponse> {
            override fun onResponse(call: Call<com.example.myapplication.models.ApiResponse>, response: Response<com.example.myapplication.models.ApiResponse>) {
                progressDialog.dismiss()
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@AdminSettingsActivity, "Backup created successfully!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@AdminSettingsActivity, "Backup failed: ${response.body()?.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<com.example.myapplication.models.ApiResponse>, t: Throwable) {
                progressDialog.dismiss()
                Toast.makeText(this@AdminSettingsActivity, "Network Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showBackupHistory() {
        val progressDialog = android.app.ProgressDialog(this)
        progressDialog.setMessage("Loading backup history...")
        progressDialog.show()

        RetrofitClient.instance.getBackupHistory().enqueue(object : Callback<BackupHistoryResponse> {
            override fun onResponse(call: Call<BackupHistoryResponse>, response: Response<BackupHistoryResponse>) {
                progressDialog.dismiss()
                if (response.isSuccessful && response.body()?.success == true) {
                    val backups = response.body()?.backups ?: emptyList()
                    if (backups.isEmpty()) {
                        Toast.makeText(this@AdminSettingsActivity, "No backups found", Toast.LENGTH_SHORT).show()
                    } else {
                        displayBackupHistoryDialog(backups)
                    }
                } else {
                    Toast.makeText(this@AdminSettingsActivity, "Failed to load history", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<BackupHistoryResponse>, t: Throwable) {
                progressDialog.dismiss()
                Toast.makeText(this@AdminSettingsActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun displayBackupHistoryDialog(backups: List<BackupFile>) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_backup_history, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rv_backups)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        recyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class BackupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
                val tvName = view.findViewById<TextView>(R.id.tv_filename)
                val tvDetails = view.findViewById<TextView>(R.id.tv_details)
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_backup_file, parent, false)
                return BackupViewHolder(v)
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val h = holder as BackupViewHolder
                val item = backups[position]
                h.tvName.text = item.filename
                h.tvDetails.text = "${item.size} • ${item.date}"
                
                h.itemView.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item.url))
                    startActivity(intent)
                    Toast.makeText(this@AdminSettingsActivity, "Downloading backup...", Toast.LENGTH_SHORT).show()
                }

                h.itemView.findViewById<View>(R.id.btn_delete_backup).setOnClickListener {
                    confirmDeleteBackup(item.filename) {
                        alertDialog.dismiss()
                        showBackupHistory() // Refresh
                    }
                }
            }

            override fun getItemCount() = backups.size
        }

        dialogView.findViewById<View>(R.id.btn_close).setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

    private fun confirmDeleteBackup(filename: String, onDeleted: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Delete Backup")
            .setMessage("Are you sure you want to delete this backup file? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                RetrofitClient.instance.deleteBackup(filename).enqueue(object : Callback<com.example.myapplication.models.ApiResponse> {
                    override fun onResponse(call: Call<com.example.myapplication.models.ApiResponse>, response: Response<com.example.myapplication.models.ApiResponse>) {
                        if (response.isSuccessful && response.body()?.success == true) {
                            Toast.makeText(this@AdminSettingsActivity, "Backup deleted", Toast.LENGTH_SHORT).show()
                            onDeleted()
                        } else {
                            Toast.makeText(this@AdminSettingsActivity, "Delete failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<com.example.myapplication.models.ApiResponse>, t: Throwable) {
                        Toast.makeText(this@AdminSettingsActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportDataToPDF() {
        val progressDialog = android.app.ProgressDialog(this)
        progressDialog.setMessage("Preparing full system export...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        // 1. Fetch Users
        RetrofitClient.instance.getUsers().enqueue(object : Callback<UsersResponse> {
            override fun onResponse(call: Call<UsersResponse>, response: Response<UsersResponse>) {
                if (response.isSuccessful && response.body()?.success == true) {
                    val residents = response.body()?.residents ?: emptyList()
                    val drivers = response.body()?.users?.filter { it.role.lowercase() == "driver" } ?: emptyList()
                    
                    // 2. Fetch Complaints
                    RetrofitClient.instance.getComplaints().enqueue(object : Callback<com.example.myapplication.models.ComplaintsResponse> {
                        override fun onResponse(call: Call<com.example.myapplication.models.ComplaintsResponse>, compResponse: Response<com.example.myapplication.models.ComplaintsResponse>) {
                            progressDialog.dismiss()
                            val complaints = compResponse.body()?.data ?: emptyList()
                            
                            // 3. Generate PDF
                            DataExporter.exportFullSystemDataPDF(this@AdminSettingsActivity, residents, drivers, complaints)
                        }

                        override fun onFailure(call: Call<com.example.myapplication.models.ComplaintsResponse>, t: Throwable) {
                            progressDialog.dismiss()
                            DataExporter.exportFullSystemDataPDF(this@AdminSettingsActivity, residents, drivers, emptyList())
                        }
                    })
                } else {
                    progressDialog.dismiss()
                    Toast.makeText(this@AdminSettingsActivity, "Failed to fetch user data", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<UsersResponse>, t: Throwable) {
                progressDialog.dismiss()
                Toast.makeText(this@AdminSettingsActivity, "Network Error", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showModal(layoutResId: Int) {
        val dialogView = LayoutInflater.from(this).inflate(layoutResId, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<android.view.View>(R.id.btn_close)?.setOnClickListener {
            alertDialog.dismiss()
        }

        // Setup status dropdown if present in the layout
        val statusSpinner = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.spinner_status)
        if (statusSpinner != null) {
            val statuses = arrayOf("Active", "Inactive", "Maintenance")
            val adapter = android.widget.ArrayAdapter(this, R.layout.dropdown_item, statuses)
            statusSpinner.setAdapter(adapter)
            statusSpinner.setText(statuses[0], false)
        }

        if (layoutResId == R.layout.dialog_change_password) {
            val etNewPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_new_password)
            val etConfirmPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_confirm_password)
            val tilNewPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_new_password)
            val tilConfirmPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_confirm_password)

            dialogView.findViewById<MaterialButton>(R.id.btn_save_password)?.setOnClickListener {
                val newPassword = etNewPassword?.text.toString()
                val confirmPassword = etConfirmPassword?.text.toString()

                var isValid = true

                // Password validation regex: 8+ chars, upper, lower, number, symbol
                val passwordPattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#\$%^&+=!])(?=\\S+\$).{8,}\$"
                
                if (!newPassword.matches(passwordPattern.toRegex())) {
                    tilNewPassword?.error = "Password must be at least 8 characters with upper, lower, number, and symbol"
                    isValid = false
                } else {
                    tilNewPassword?.error = null
                }

                if (newPassword != confirmPassword) {
                    tilConfirmPassword?.error = "Passwords do not match"
                    isValid = false
                } else {
                    tilConfirmPassword?.error = null
                }

                if (isValid) {
                    // Handle password update
                    alertDialog.dismiss()
                }
            }
        }

        alertDialog.show()
    }

    private fun showLogoutConfirmation() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_logout_confirmation, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<android.widget.Button>(R.id.btn_cancel).setOnClickListener {
            alertDialog.dismiss()
        }

        dialogView.findViewById<android.widget.Button>(R.id.btn_confirm_logout).setOnClickListener {
            sessionManager.logout()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            alertDialog.dismiss()
            finish()
        }

        alertDialog.show()
    }

    private fun setupHeader() {
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            val intent = Intent(this, AdminDashboardActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }
    }

    private fun setupProfile() {
        val user = sessionManager.getUser()
        findViewById<TextView>(R.id.tv_profile_name).text = user?.name ?: "Admin User"
        findViewById<TextView>(R.id.tv_profile_email).text = user?.email ?: "admin@balintawak.gov"
        findViewById<TextView>(R.id.tv_profile_contact).text = user?.phone ?: "09171234567"
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_settings

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_monitor -> {
                    startActivity(Intent(this, AdminDashboardActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_track -> {
                    startActivity(Intent(this, TrackTrucksActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_reports -> {
                    startActivity(Intent(this, AnalyticsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_complaints -> {
                    startActivity(Intent(this, ComplaintsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_settings -> true
                else -> false
            }
        }
    }
}