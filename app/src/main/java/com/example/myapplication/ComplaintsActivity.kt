package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

import com.example.myapplication.models.ComplaintsResponse
import com.example.myapplication.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.CheckBox
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.models.SystemNotification
import com.google.android.material.tabs.TabLayout
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ComplaintsActivity : AppCompatActivity() {

    private lateinit var complaintsContainer: LinearLayout
    private lateinit var tvTotalComplaints: TextView
    private lateinit var tvPendingCount: TextView
    private lateinit var tvInProgressCount: TextView
    private lateinit var tvResolvedCount: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var tvHeaderTitle: TextView

    private val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
    private val database = FirebaseDatabase.getInstance(dbUrl)
    private val issuesList = mutableListOf<SystemNotification>()
    private var residentComplaints = listOf<com.example.myapplication.models.Complaint>()
    private var isShowingAll = false
    private lateinit var btnViewAll: View

    private var selectedComplaintIds = mutableSetOf<Int>()
    private var selectedIssueIds = mutableSetOf<String>()
    private var isSelectionMode = false
    private lateinit var layoutBulkActions: LinearLayout
    private lateinit var cbSelectAll: CheckBox
    private lateinit var btnBulkDelete: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_complaints)

        val root = findViewById<View>(R.id.complaints_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        complaintsContainer = findViewById(R.id.complaintsContainer)
        tvTotalComplaints = findViewById(R.id.tvTotalComplaints)
        tvPendingCount = findViewById(R.id.tvPendingCount)
        tvInProgressCount = findViewById(R.id.tvInProgressCount)
        tvResolvedCount = findViewById(R.id.tvResolvedCount)
        tabLayout = findViewById(R.id.tabLayout)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        btnViewAll = findViewById(R.id.btnViewAll)
        layoutBulkActions = findViewById(R.id.layoutBulkActions)
        cbSelectAll = findViewById(R.id.cbSelectAll)
        btnBulkDelete = findViewById(R.id.btnBulkDelete)

        btnViewAll.setOnClickListener {
            isShowingAll = true
            updateUI()
        }

        cbSelectAll.setOnClickListener {
            val isChecked = (it as CheckBox).isChecked
            if (tabLayout.selectedTabPosition == 0) {
                val displayList = if (isShowingAll) residentComplaints else residentComplaints.take(12)
                if (isChecked) selectedComplaintIds.addAll(displayList.map { it.id })
                else selectedComplaintIds.clear()
            } else {
                val displayList = if (isShowingAll) issuesList else issuesList.take(12)
                if (isChecked) selectedIssueIds.addAll(displayList.map { it.id })
                else selectedIssueIds.clear()
            }
            updateUI()
        }

        btnBulkDelete.setOnClickListener {
            performBulkDelete()
        }

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            val intent = Intent(this, AdminDashboardActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                isShowingAll = false
                exitSelectionMode()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        setupBottomNavigation()
        fetchComplaints()
        fetchDriverIssues()

        // Handle intent extra to select tab
        val initialTab = intent.getIntExtra("SELECTED_TAB", 0)
        tabLayout.getTabAt(initialTab)?.select()
    }

    private fun fetchComplaints() {
        RetrofitClient.instance.getComplaints().enqueue(object : Callback<ComplaintsResponse> {
            override fun onResponse(call: Call<ComplaintsResponse>, response: Response<ComplaintsResponse>) {
                if (response.isSuccessful && response.body()?.success == true) {
                    residentComplaints = (response.body()?.data ?: emptyList()).sortedByDescending { it.createdAt }
                    if (tabLayout.selectedTabPosition == 0) updateUI()
                } else {
                    Toast.makeText(this@ComplaintsActivity, "Failed to load complaints", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ComplaintsResponse>, t: Throwable) {
                Toast.makeText(this@ComplaintsActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchDriverIssues() {
        database.getReference("notifications")
            .orderByChild("type")
            .equalTo("DRIVER_ISSUE")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    issuesList.clear()
                    for (child in snapshot.children) {
                        val notification = child.getValue(SystemNotification::class.java)
                        if (notification != null) {
                            notification.id = child.key ?: ""
                            issuesList.add(notification)
                        }
                    }
                    issuesList.sortByDescending { it.timestamp }
                    if (tabLayout.selectedTabPosition == 1) updateUI()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun updateUI() {
        complaintsContainer.removeAllViews()
        var pending = 0
        var inProgress = 0
        var resolved = 0

        layoutBulkActions.visibility = if (isSelectionMode) View.VISIBLE else View.GONE

        if (tabLayout.selectedTabPosition == 0) {
            tvHeaderTitle.text = "Resident Complaints"
            tvTotalComplaints.text = "${residentComplaints.size} total"
            
            for (complaint in residentComplaints) {
                val status = complaint.status.uppercase().replace("_", " ")
                when (status) {
                    "PENDING" -> pending++
                    "IN PROGRESS" -> inProgress++
                    "RESOLVED" -> resolved++
                }
            }

            if (residentComplaints.size > 12 && !isShowingAll) {
                btnViewAll.visibility = View.VISIBLE
            } else {
                btnViewAll.visibility = View.GONE
            }

            val displayList = if (isShowingAll) residentComplaints else residentComplaints.take(12)
            cbSelectAll.isChecked = displayList.isNotEmpty() && selectedComplaintIds.size >= displayList.size
            for (complaint in displayList) {
                addComplaintCard(complaint)
            }
        } else {
            tvHeaderTitle.text = "Driver Issues"
            tvTotalComplaints.text = "${issuesList.size} total"
            
            for (issue in issuesList) {
                val status = (issue.status ?: "PENDING").uppercase()
                when (status) {
                    "PENDING", "" -> pending++
                    "IN PROGRESS" -> inProgress++
                    "RESOLVED" -> resolved++
                }
            }

            if (issuesList.size > 12 && !isShowingAll) {
                btnViewAll.visibility = View.VISIBLE
            } else {
                btnViewAll.visibility = View.GONE
            }

            val displayList = if (isShowingAll) issuesList else issuesList.take(12)
            cbSelectAll.isChecked = displayList.isNotEmpty() && selectedIssueIds.size >= displayList.size
            for (issue in displayList) {
                addIssueCard(issue)
            }
        }

        tvPendingCount.text = pending.toString()
        tvInProgressCount.text = inProgress.toString()
        tvResolvedCount.text = resolved.toString()
    }

    private fun addComplaintCard(complaint: com.example.myapplication.models.Complaint) {
        val cardView = LayoutInflater.from(this).inflate(R.layout.item_complaint, complaintsContainer, false)
        val cbSelect = cardView.findViewById<CheckBox>(R.id.cbSelect)
        val tvCategory = cardView.findViewById<TextView>(R.id.tvCategory)
        val tvStatus = cardView.findViewById<TextView>(R.id.tvStatus)
        val tvResidentName = cardView.findViewById<TextView>(R.id.tvResidentName)
        val tvDescription = cardView.findViewById<TextView>(R.id.tvDescription)
        val tvDate = cardView.findViewById<TextView>(R.id.tvDate)
        val layoutActions = cardView.findViewById<LinearLayout>(R.id.layoutActions)
        val layoutAdminResponse = cardView.findViewById<LinearLayout>(R.id.layoutAdminResponse)
        val tvAdminResponse = cardView.findViewById<TextView>(R.id.tvAdminResponse)
        val btnInProgress = cardView.findViewById<View>(R.id.btnInProgress)
        val btnResolve = cardView.findViewById<View>(R.id.btnResolve)
        val layoutResolvedDate = cardView.findViewById<LinearLayout>(R.id.layoutResolvedDate)
        val tvResolvedDate = cardView.findViewById<TextView>(R.id.tvResolvedDate)

        cbSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        cbSelect.isChecked = selectedComplaintIds.contains(complaint.id)
        cbSelect.setOnClickListener {
            val isChecked = (it as CheckBox).isChecked
            if (isChecked) selectedComplaintIds.add(complaint.id)
            else selectedComplaintIds.remove(complaint.id)
            if (selectedComplaintIds.isEmpty()) exitSelectionMode()
            else updateUI()
        }

        tvCategory.text = complaint.category
        val status = complaint.status.uppercase().replace("_", " ")
        tvStatus.text = status
        tvResidentName.text = "${complaint.fullName} • ${complaint.purok}"
        tvDescription.text = complaint.description
        tvDate.text = complaint.createdAt

        applyStatusStyle(tvStatus, status, layoutActions, btnInProgress, layoutAdminResponse, tvAdminResponse, complaint.adminResponse, layoutResolvedDate, tvResolvedDate, complaint.updatedAt ?: complaint.createdAt)

        btnInProgress.setOnClickListener { updateComplaintStatus(complaint.id, "in_progress") }
        btnResolve.setOnClickListener { showResolveDialog(complaint.id.toString(), true) }

        cardView.setOnClickListener {
            if (isSelectionMode) cbSelect.performClick()
        }

        cardView.setOnLongClickListener {
            if (!isSelectionMode) enterSelectionMode(complaint.id, null)
            true
        }

        complaintsContainer.addView(cardView)
    }

    private fun deleteComplaintFromServer(id: Int) {
        RetrofitClient.instance.deleteComplaint(id, "delete").enqueue(object : Callback<com.example.myapplication.models.ApiResponse> {
            override fun onResponse(call: Call<com.example.myapplication.models.ApiResponse>, response: Response<com.example.myapplication.models.ApiResponse>) {
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@ComplaintsActivity, "Complaint deleted", Toast.LENGTH_SHORT).show()
                    fetchComplaints()
                } else {
                    Toast.makeText(this@ComplaintsActivity, "Delete failed", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<com.example.myapplication.models.ApiResponse>, t: Throwable) {
                Toast.makeText(this@ComplaintsActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun addIssueCard(issue: SystemNotification) {
        val cardView = LayoutInflater.from(this).inflate(R.layout.item_complaint, complaintsContainer, false)
        val cbSelect = cardView.findViewById<CheckBox>(R.id.cbSelect)
        val tvCategory = cardView.findViewById<TextView>(R.id.tvCategory)
        val tvStatus = cardView.findViewById<TextView>(R.id.tvStatus)
        val tvResidentName = cardView.findViewById<TextView>(R.id.tvResidentName)
        val tvDescription = cardView.findViewById<TextView>(R.id.tvDescription)
        val tvDate = cardView.findViewById<TextView>(R.id.tvDate)
        val layoutActions = cardView.findViewById<LinearLayout>(R.id.layoutActions)
        val layoutAdminResponse = cardView.findViewById<LinearLayout>(R.id.layoutAdminResponse)
        val tvAdminResponse = cardView.findViewById<TextView>(R.id.tvAdminResponse)
        val btnInProgress = cardView.findViewById<View>(R.id.btnInProgress)
        val btnResolve = cardView.findViewById<View>(R.id.btnResolve)
        val layoutResolvedDate = cardView.findViewById<LinearLayout>(R.id.layoutResolvedDate)
        val tvResolvedDate = cardView.findViewById<TextView>(R.id.tvResolvedDate)

        cbSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        cbSelect.isChecked = selectedIssueIds.contains(issue.id)
        cbSelect.setOnClickListener {
            val isChecked = (it as CheckBox).isChecked
            if (isChecked) selectedIssueIds.add(issue.id)
            else selectedIssueIds.remove(issue.id)
            if (selectedIssueIds.isEmpty()) exitSelectionMode()
            else updateUI()
        }

        tvCategory.text = issue.title.replace("New Driver Issue: ", "")
        val status = (issue.status ?: "PENDING").uppercase()
        tvStatus.text = if (status.isEmpty()) "PENDING" else status
        tvResidentName.text = issue.message.substringBefore(" reported an issue:")
        tvDescription.text = issue.message.substringAfter(" reported an issue: ")
        
        tvDate.text = android.text.format.DateUtils.getRelativeTimeSpanString(issue.timestamp, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)

        applyStatusStyle(tvStatus, tvStatus.text.toString(), layoutActions, btnInProgress, layoutAdminResponse, tvAdminResponse, issue.adminResponse, layoutResolvedDate, tvResolvedDate, "Resolved")

        btnInProgress.setOnClickListener { updateIssueStatus(issue.id, "IN PROGRESS") }
        btnResolve.setOnClickListener { showResolveDialog(issue.id, false) }

        cardView.setOnClickListener {
            if (isSelectionMode) cbSelect.performClick()
        }

        cardView.setOnLongClickListener {
            if (!isSelectionMode) enterSelectionMode(null, issue.id)
            true
        }

        complaintsContainer.addView(cardView)
    }

    private fun enterSelectionMode(complaintId: Int?, issueId: String?) {
        isSelectionMode = true
        if (complaintId != null) selectedComplaintIds.add(complaintId)
        if (issueId != null) selectedIssueIds.add(issueId)
        updateUI()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedComplaintIds.clear()
        selectedIssueIds.clear()
        updateUI()
    }

    private fun performBulkDelete() {
        if (tabLayout.selectedTabPosition == 0) {
            val selected = residentComplaints.filter { selectedComplaintIds.contains(it.id) }
            val hasUnresolved = selected.any { it.status.uppercase() != "RESOLVED" }
            if (hasUnresolved) {
                Toast.makeText(this, "You need to resolve the issues first before deleting it.", Toast.LENGTH_LONG).show()
                return
            }
            
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Selected")
                .setMessage("Are you sure you want to delete ${selected.size} resolved complaints?")
                .setPositiveButton("Delete") { _, _ ->
                    val idsString = selectedComplaintIds.joinToString(",")
                    RetrofitClient.instance.bulkDeleteComplaints(idsString).enqueue(object : Callback<com.example.myapplication.models.ApiResponse> {
                        override fun onResponse(call: Call<com.example.myapplication.models.ApiResponse>, response: Response<com.example.myapplication.models.ApiResponse>) {
                            if (response.isSuccessful && response.body()?.success == true) {
                                Toast.makeText(this@ComplaintsActivity, "Complaints deleted", Toast.LENGTH_SHORT).show()
                                exitSelectionMode()
                                fetchComplaints()
                            }
                        }
                        override fun onFailure(call: Call<com.example.myapplication.models.ApiResponse>, t: Throwable) {}
                    })
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            val selected = issuesList.filter { selectedIssueIds.contains(it.id) }
            val hasUnresolved = selected.any { it.status?.uppercase() != "RESOLVED" }
            if (hasUnresolved) {
                Toast.makeText(this, "You need to resolve the issues first before deleting it.", Toast.LENGTH_LONG).show()
                return
            }

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Selected")
                .setMessage("Are you sure you want to delete ${selected.size} resolved issues?")
                .setPositiveButton("Delete") { _, _ ->
                    val total = selectedIssueIds.size
                    var count = 0
                    for (id in selectedIssueIds) {
                        database.getReference("notifications").child(id).removeValue()
                            .addOnSuccessListener {
                                count++
                                if (count == total) {
                                    Toast.makeText(this@ComplaintsActivity, "Issues deleted", Toast.LENGTH_SHORT).show()
                                    exitSelectionMode()
                                }
                            }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun applyStatusStyle(tvStatus: TextView, status: String, layoutActions: View, btnInProgress: View, layoutAdminResponse: View, tvAdminResponse: TextView, responseText: String?, layoutResolvedDate: View, tvResolvedDate: TextView, dateText: String) {
        when (status) {
            "PENDING" -> {
                tvStatus.setTextColor(android.graphics.Color.parseColor("#F9A825"))
                tvStatus.background.setTint(android.graphics.Color.parseColor("#FFF9C4"))
                layoutActions.visibility = View.VISIBLE
                btnInProgress.visibility = View.VISIBLE
                layoutAdminResponse.visibility = View.GONE
                layoutResolvedDate.visibility = View.GONE
            }
            "IN PROGRESS" -> {
                tvStatus.setTextColor(android.graphics.Color.parseColor("#1E88E5"))
                tvStatus.background.setTint(android.graphics.Color.parseColor("#E3F2FD"))
                layoutActions.visibility = View.VISIBLE
                btnInProgress.visibility = View.GONE
                layoutAdminResponse.visibility = View.GONE
                layoutResolvedDate.visibility = View.GONE
            }
            "RESOLVED" -> {
                tvStatus.setTextColor(android.graphics.Color.parseColor("#43A047"))
                tvStatus.background.setTint(android.graphics.Color.parseColor("#E8F5E9"))
                layoutActions.visibility = View.GONE
                layoutAdminResponse.visibility = View.VISIBLE
                tvAdminResponse.text = responseText ?: "No response provided."
                layoutResolvedDate.visibility = View.VISIBLE
                tvResolvedDate.text = if (dateText == "Resolved") "Resolved" else "Resolved: $dateText"
            }
        }
    }

    private fun updateComplaintStatus(id: Int, status: String, response: String? = null) {
        RetrofitClient.instance.updateComplaint(id, status, response).enqueue(object : Callback<com.example.myapplication.models.ApiResponse> {
            override fun onResponse(call: Call<com.example.myapplication.models.ApiResponse>, response: Response<com.example.myapplication.models.ApiResponse>) {
                if (response.isSuccessful && response.body()?.success == true) fetchComplaints()
                else Toast.makeText(this@ComplaintsActivity, "Update failed", Toast.LENGTH_SHORT).show()
            }
            override fun onFailure(call: Call<com.example.myapplication.models.ApiResponse>, t: Throwable) {
                Toast.makeText(this@ComplaintsActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateIssueStatus(id: String, status: String, response: String? = null) {
        val updates = mutableMapOf<String, Any>("status" to status)
        if (response != null) updates["adminResponse"] = response
        database.getReference("notifications").child(id).updateChildren(updates)
            .addOnSuccessListener { Toast.makeText(this, "Status updated", Toast.LENGTH_SHORT).show() }
    }

    private fun showResolveDialog(id: String, isComplaint: Boolean) {
        val editText = android.widget.EditText(this)
        editText.hint = "Enter admin response"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Resolve")
            .setView(editText)
            .setPositiveButton("Resolve") { _, _ ->
                if (isComplaint) updateComplaintStatus(id.toInt(), "RESOLVED", editText.text.toString())
                else updateIssueStatus(id, "RESOLVED", editText.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupBottomNavigation() {
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        if (bottomNavigationView != null) {
            bottomNavigationView.selectedItemId = R.id.nav_complaints
            bottomNavigationView.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_monitor -> { startActivity(Intent(this, AdminDashboardActivity::class.java)); overridePendingTransition(0, 0); finish(); true }
                    R.id.nav_track -> { startActivity(Intent(this, TrackTrucksActivity::class.java)); overridePendingTransition(0, 0); finish(); true }
                    R.id.nav_reports -> { startActivity(Intent(this, AnalyticsActivity::class.java)); overridePendingTransition(0, 0); finish(); true }
                    R.id.nav_complaints -> true
                    R.id.nav_settings -> { startActivity(Intent(this, AdminSettingsActivity::class.java)); overridePendingTransition(0, 0); finish(); true }
                    else -> false
                }
            }
        }
    }
}