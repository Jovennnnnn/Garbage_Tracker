package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.models.ApiResponse
import com.example.myapplication.models.Complaint
import com.example.myapplication.models.ComplaintsResponse
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.utils.SessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class ResidentComplaintsActivity : AppCompatActivity() {

    private lateinit var complaintsContainer: LinearLayout
    private lateinit var tvTotalComplaints: TextView
    private lateinit var tvPendingCount: TextView
    private lateinit var tvInProgressCount: TextView
    private lateinit var tvResolvedCount: TextView
    private lateinit var sessionManager: SessionManager

    private var selectedComplaintIds = mutableSetOf<Int>()
    private var allComplaints = listOf<Complaint>()
    private var isSelectionMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_resident_complaints)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.resident_complaints_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sessionManager = SessionManager(this)
        complaintsContainer = findViewById(R.id.complaints_container)
        tvTotalComplaints = findViewById(R.id.tv_total_complaints)
        tvPendingCount = findViewById(R.id.tv_pending_count)
        tvInProgressCount = findViewById(R.id.tv_in_progress_count)
        tvResolvedCount = findViewById(R.id.tv_resolved_count)

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.btn_new_complaint).setOnClickListener {
            showNewComplaintDialog()
        }

        val cbSelectAll = findViewById<CheckBox>(R.id.cb_select_all)
        val btnBulkDelete = findViewById<View>(R.id.btn_bulk_delete)

        cbSelectAll.setOnClickListener {
            val isChecked = (it as CheckBox).isChecked
            if (isChecked) {
                selectedComplaintIds.addAll(allComplaints.map { complaint -> complaint.id })
            } else {
                selectedComplaintIds.clear()
            }
            updateUI(allComplaints)
        }

        findViewById<View>(R.id.btn_back).setOnClickListener {
            if (isSelectionMode) {
                exitSelectionMode()
            } else {
                finish()
            }
        }

        btnBulkDelete.setOnClickListener {
            if (selectedComplaintIds.isEmpty()) return@setOnClickListener
            
            AlertDialog.Builder(this)
                .setTitle("Delete Selected")
                .setMessage("Are you sure you want to delete ${selectedComplaintIds.size} complaints from your view?")
                .setPositiveButton("Delete") { _, _ ->
                    bulkDeleteFromServer(selectedComplaintIds.toList())
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        setupBottomNavigation()
        fetchMyComplaints()
    }

    private fun bulkDeleteFromServer(ids: List<Int>) {
        val idsString = ids.joinToString(",")
        RetrofitClient.instance.bulkDeleteComplaints(idsString)
            .enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        Toast.makeText(this@ResidentComplaintsActivity, response.body()?.message, Toast.LENGTH_SHORT).show()
                        selectedComplaintIds.clear()
                        fetchMyComplaints()
                    } else {
                        Toast.makeText(this@ResidentComplaintsActivity, "Bulk delete failed", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    Toast.makeText(this@ResidentComplaintsActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    override fun onResume() {
        super.onResume()
        fetchMyComplaints()
    }

    private fun showNewComplaintDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_file_complaint, null)
        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialog)
        builder.setView(dialogView)
        
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.spinner_category)
        val etDescription = dialogView.findViewById<EditText>(R.id.et_description)
        val btnSubmit = dialogView.findViewById<View>(R.id.btn_submit)
        val btnClose = dialogView.findViewById<View>(R.id.btn_close)

        val categories = arrayOf("Uncollected Garbage", "Spilled Waste", "Driver Behavior", "Schedule Issue", "Other")
        val adapter = ArrayAdapter(this, R.layout.spinner_item, categories)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerCategory.adapter = adapter

        btnClose.setOnClickListener { dialog.dismiss() }

        btnSubmit.setOnClickListener {
            val category = spinnerCategory.selectedItem.toString()
            val description = etDescription.text.toString().trim()

            if (description.isEmpty()) {
                etDescription.error = "Please enter description"
                return@setOnClickListener
            }

            submitComplaint(category, description, dialog)
        }

        dialog.show()
    }

    private fun submitComplaint(category: String, description: String, dialog: AlertDialog) {
        val userId = sessionManager.getUser()?.userId ?: return
        
        RetrofitClient.instance.fileComplaint(userId.toString(), category, description)
            .enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        Toast.makeText(this@ResidentComplaintsActivity, "Complaint submitted successfully", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        fetchMyComplaints()
                    } else {
                        Toast.makeText(this@ResidentComplaintsActivity, "Failed to submit complaint", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    Toast.makeText(this@ResidentComplaintsActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun deleteComplaintFromServer(complaintId: Int, action: String) {
        RetrofitClient.instance.deleteComplaint(complaintId, action)
            .enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        Toast.makeText(this@ResidentComplaintsActivity, response.body()?.message, Toast.LENGTH_SHORT).show()
                        fetchMyComplaints()
                    } else {
                        Toast.makeText(this@ResidentComplaintsActivity, "Failed to $action complaint", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    Toast.makeText(this@ResidentComplaintsActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun fetchMyComplaints() {
        RetrofitClient.instance.getComplaints().enqueue(object : Callback<ComplaintsResponse> {
            override fun onResponse(call: Call<ComplaintsResponse>, response: Response<ComplaintsResponse>) {
                if (response.isSuccessful && response.body()?.success == true) {
                    val complaints = response.body()?.data ?: emptyList()
                    val currentUserId = sessionManager.getUser()?.userId ?: -1
                    allComplaints = complaints.filter { it.userId == currentUserId && it.deletedByResident == 0 }
                    updateUI(allComplaints)
                } else {
                    Toast.makeText(this@ResidentComplaintsActivity, "Failed to load complaints", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ComplaintsResponse>, t: Throwable) {
                Toast.makeText(this@ResidentComplaintsActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateUI(complaints: List<Complaint>) {
        tvTotalComplaints.text = "${complaints.size} total"
        complaintsContainer.removeAllViews()

        val layoutBulkActions = findViewById<LinearLayout>(R.id.layout_bulk_actions)
        layoutBulkActions.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        
        val cbSelectAll = findViewById<CheckBox>(R.id.cb_select_all)
        cbSelectAll.isChecked = complaints.isNotEmpty() && selectedComplaintIds.size == complaints.size

        var pendingCount = 0
        var inProgressCount = 0
        var resolvedCount = 0

        for (complaint in complaints) {
            when (complaint.status.uppercase()) {
                "PENDING" -> pendingCount++
                "IN PROGRESS" -> inProgressCount++
                "RESOLVED" -> resolvedCount++
            }

            val cardView = LayoutInflater.from(this).inflate(R.layout.item_complaint, complaintsContainer, false)
            
            val cbSelect = cardView.findViewById<CheckBox>(R.id.cbSelect)
            cbSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            cbSelect.isChecked = selectedComplaintIds.contains(complaint.id)
            cbSelect.setOnClickListener {
                val isChecked = (it as CheckBox).isChecked
                if (isChecked) {
                    selectedComplaintIds.add(complaint.id)
                } else {
                    selectedComplaintIds.remove(complaint.id)
                }
                cbSelectAll.isChecked = selectedComplaintIds.size == complaints.size
                if (selectedComplaintIds.isEmpty()) exitSelectionMode()
            }

            cardView.setOnLongClickListener {
                if (!isSelectionMode) {
                    enterSelectionMode(complaint.id)
                }
                true
            }

            cardView.setOnClickListener {
                if (isSelectionMode) {
                    cbSelect.performClick()
                } else {
                    val createdAtDate = try { 
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(complaint.createdAt) 
                    } catch (_: Exception) { null }
                    val diffMs = if (createdAtDate != null) Date().time - createdAtDate.time else 999999L
                    val isWithinGracePeriod = diffMs < 10000

                    showComplaintDetail(complaint, isWithinGracePeriod)
                }
            }

            val tvCategory = cardView.findViewById<TextView>(R.id.tvCategory)
            val tvStatus = cardView.findViewById<TextView>(R.id.tvStatus)
            val tvDate = cardView.findViewById<TextView>(R.id.tvDate)
            val layoutActions = cardView.findViewById<LinearLayout>(R.id.layoutActions)
            val layoutAdminResponse = cardView.findViewById<LinearLayout>(R.id.layoutAdminResponse)
            val tvAdminResponse = cardView.findViewById<TextView>(R.id.tvAdminResponse)
            val layoutResolvedDate = cardView.findViewById<LinearLayout>(R.id.layoutResolvedDate)
            val tvResolvedDate = cardView.findViewById<TextView>(R.id.tvResolvedDate)

            tvCategory.text = complaint.category
            tvStatus.text = complaint.status.uppercase()
            tvDate.text = complaint.createdAt

            layoutActions.visibility = View.GONE

            when (complaint.status.uppercase()) {
                "PENDING" -> {
                    tvStatus.setTextColor(android.graphics.Color.parseColor("#00796B"))
                    tvStatus.background.setTint(android.graphics.Color.parseColor("#B2DFDB"))
                    layoutAdminResponse.visibility = View.GONE
                    layoutResolvedDate.visibility = View.GONE
                }
                "IN PROGRESS" -> {
                    tvStatus.setTextColor(android.graphics.Color.parseColor("#004D40"))
                    tvStatus.background.setTint(android.graphics.Color.parseColor("#80CBC4"))
                    layoutAdminResponse.visibility = View.GONE
                    layoutResolvedDate.visibility = View.GONE
                }
                "RESOLVED" -> {
                    tvStatus.setTextColor(android.graphics.Color.parseColor("#1B5E20"))
                    tvStatus.background.setTint(android.graphics.Color.parseColor("#C8E6C9"))
                    layoutAdminResponse.visibility = View.VISIBLE
                    tvAdminResponse.text = complaint.adminResponse ?: "No response yet."
                    layoutResolvedDate.visibility = View.VISIBLE
                    tvResolvedDate.text = "Resolved: ${complaint.updatedAt ?: complaint.createdAt}"
                }
            }

            complaintsContainer.addView(cardView)
        }

        tvPendingCount.text = pendingCount.toString()
        tvInProgressCount.text = inProgressCount.toString()
        tvResolvedCount.text = resolvedCount.toString()
    }

    private fun enterSelectionMode(firstId: Int) {
        isSelectionMode = true
        selectedComplaintIds.add(firstId)
        updateUI(allComplaints)
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedComplaintIds.clear()
        updateUI(allComplaints)
    }

    private fun showComplaintDetail(complaint: Complaint, canUndo: Boolean) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_complaint_detail, null)
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
            .setView(dialogView)
            .create()

        val tvCategory = dialogView.findViewById<TextView>(R.id.tvDetailCategory)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvDetailStatus)
        val tvDescription = dialogView.findViewById<TextView>(R.id.tvDetailDescription)
        val tvDate = dialogView.findViewById<TextView>(R.id.tvDetailDate)
        val layoutAdminResponse = dialogView.findViewById<LinearLayout>(R.id.layoutDetailAdminResponse)
        val tvAdminResponse = dialogView.findViewById<TextView>(R.id.tvDetailAdminResponse)
        val btnClose = dialogView.findViewById<View>(R.id.btnDetailClose)

        // Add Undo/Delete buttons to detail if needed
        val detailRoot = dialogView as LinearLayout
        if (canUndo) {
            val btnUndo = MaterialButton(this).apply {
                text = "Undo Submission"
                setOnClickListener {
                    deleteComplaintFromServer(complaint.id, "undo")
                    dialog.dismiss()
                }
            }
            detailRoot.addView(btnUndo, detailRoot.childCount - 1)
        } else {
            val btnDelete = MaterialButton(this).apply {
                text = "Delete from View"
                setOnClickListener {
                    AlertDialog.Builder(this@ResidentComplaintsActivity)
                        .setTitle("Delete")
                        .setMessage("Delete from your view?")
                        .setPositiveButton("Delete") { _, _ ->
                            deleteComplaintFromServer(complaint.id, "delete")
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            detailRoot.addView(btnDelete, detailRoot.childCount - 1)
        }

        tvCategory.text = complaint.category
        tvStatus.text = complaint.status.uppercase()
        tvDescription.text = complaint.description
        tvDate.text = "Filed on: ${complaint.createdAt}"

        when (complaint.status.uppercase()) {
            "PENDING" -> {
                tvStatus.setTextColor(android.graphics.Color.parseColor("#00796B"))
                tvStatus.background.setTint(android.graphics.Color.parseColor("#B2DFDB"))
            }
            "IN PROGRESS" -> {
                tvStatus.setTextColor(android.graphics.Color.parseColor("#004D40"))
                tvStatus.background.setTint(android.graphics.Color.parseColor("#80CBC4"))
            }
            "RESOLVED" -> {
                tvStatus.setTextColor(android.graphics.Color.parseColor("#1B5E20"))
                tvStatus.background.setTint(android.graphics.Color.parseColor("#C8E6C9"))
                layoutAdminResponse.visibility = View.VISIBLE
                tvAdminResponse.text = complaint.adminResponse ?: "No response yet."
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_complaints

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, ResidentDashboardActivity::class.java))
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
                R.id.nav_complaints -> true
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
