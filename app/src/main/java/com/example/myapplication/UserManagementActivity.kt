package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import com.google.android.material.datepicker.MaterialDatePicker
import java.util.*
import java.text.SimpleDateFormat
import androidx.core.util.Pair
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.graphics.Color
import androidx.appcompat.app.AlertDialog
import com.example.myapplication.adapters.CalendarAdapter
import com.example.myapplication.models.*
import com.example.myapplication.network.RetrofitClient
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.google.firebase.database.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class UserManagementActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageButton
    private lateinit var containerUsers: LinearLayout
    private lateinit var tvEmptyState: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var pbLoading: ProgressBar

    private lateinit var btnDatePicker: View
    private lateinit var tvDateRange: TextView
    private lateinit var layoutPurokFilter: View
    private lateinit var actvPurokFilter: AutoCompleteTextView
    
    private lateinit var btnSelectMode: ImageButton
    private lateinit var btnShowArchive: ImageButton
    private lateinit var layoutSelectionActions: View
    private lateinit var btnCancelSelection: ImageButton
    private lateinit var tvSelectionCount: TextView
    private lateinit var btnBulkArchive: ImageButton
    private lateinit var btnBulkDelete: ImageButton
    private lateinit var tvActiveFilterLabel: TextView

    private var startDate: Long? = null
    private var endDate: Long? = null
    private var selectedPurokFilter = "All Puroks"
    private var isShowArchivedOnly = false
    private var isSelectionMode = false
    private val selectedUsers = mutableSetOf<UserData>()

    private var allResidents = mutableListOf<UserData>()
    private var allDrivers = mutableListOf<UserData>()
    private var allRequests = mutableListOf<UserData>()
    
    private var currentFilteredList = mutableListOf<UserData>()
    private var currentTab = 0

    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_user_management)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.user_management_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeViews()
        setupTabLayout()
        setupSearch()
        setupBottomNavigation()
        updateTabCounts() // Show initial zeros
        
        val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
        database = FirebaseDatabase.getInstance(dbUrl).reference

        fetchUsers()
        fetchFirebaseRequests()

        // Handle tab selection from intent
        val targetTab = intent.getIntExtra("TAB_INDEX", 0)
        if (targetTab > 0) {
            tabLayout.getTabAt(targetTab)?.select()
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            startActivity(Intent(this, AdminDashboardActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
    }

    private fun initializeViews() {
        etSearch = findViewById(R.id.et_search)
        btnClearSearch = findViewById(R.id.btn_clear_search)
        containerUsers = findViewById(R.id.container_users)
        tvEmptyState = findViewById(R.id.tv_empty_state)
        tabLayout = findViewById(R.id.tab_layout)
        pbLoading = findViewById(R.id.pb_loading)

        btnDatePicker = findViewById(R.id.btn_date_picker)
        tvDateRange = findViewById(R.id.tv_date_range)
        layoutPurokFilter = findViewById(R.id.layout_purok_filter)
        actvPurokFilter = findViewById(R.id.actv_purok_filter)
        
        btnSelectMode = findViewById(R.id.btn_select_mode)
        btnShowArchive = findViewById(R.id.btn_show_archive)
        layoutSelectionActions = findViewById(R.id.layout_selection_actions)
        btnCancelSelection = findViewById(R.id.btn_cancel_selection)
        tvSelectionCount = findViewById(R.id.tv_selection_count)
        btnBulkArchive = findViewById(R.id.btn_bulk_archive)
        btnBulkDelete = findViewById(R.id.btn_bulk_delete)
        tvActiveFilterLabel = findViewById(R.id.tv_active_filter_label)

        setupFilters()
    }

    private fun setupFilters() {
        btnDatePicker.setOnClickListener {
            showDateRangePicker()
        }
        
        btnDatePicker.setOnLongClickListener {
            startDate = null
            endDate = null
            tvDateRange.text = "All Time"
            filterList(etSearch.text.toString())
            true
        }

        layoutPurokFilter.setOnClickListener {
            showPurokBottomSheet()
        }

        btnSelectMode.setOnClickListener {
            toggleSelectionMode(!isSelectionMode)
        }

        btnShowArchive.setOnClickListener {
            isShowArchivedOnly = !isShowArchivedOnly
            btnShowArchive.setImageResource(if (isShowArchivedOnly) android.R.drawable.ic_menu_revert else android.R.drawable.ic_menu_save)
            tvActiveFilterLabel.visibility = if (isShowArchivedOnly) View.VISIBLE else View.GONE
            filterList(etSearch.text.toString())
        }

        btnCancelSelection.setOnClickListener {
            toggleSelectionMode(false)
        }

        btnBulkArchive.setOnClickListener {
            if (selectedUsers.isEmpty()) return@setOnClickListener
            showBulkConfirmDialog(true)
        }

        btnBulkDelete.setOnClickListener {
            if (selectedUsers.isEmpty()) return@setOnClickListener
            showBulkConfirmDialog(false) // false means permanent delete if we implement it, or just archive
        }
        
        setupPurokDropdown()
    }

    private fun showBulkConfirmDialog(isArchive: Boolean) {
        val action = if (isArchive) (if (isShowArchivedOnly) "unarchive" else "archive") else "delete"
        
        val typeName = when (currentTab) {
            0 -> "residents"
            1 -> "drivers"
            2 -> "requests"
            else -> "users"
        }

        val explanation = if (action == "archive") {
            "\n\nArchived $typeName will no longer be able to log in, and their data will be hidden from the active list. " +
            (if (currentTab == 1) "Drivers will also be automatically removed from the tracking map." else "")
        } else if (action == "unarchive") {
            "\n\nThis will restore the $typeName's access and they will be able to log in again."
        } else ""

        AlertDialog.Builder(this)
            .setTitle("Bulk ${action.replaceFirstChar { it.uppercase() }}")
            .setMessage("Are you sure you want to $action ${selectedUsers.size} selected $typeName?$explanation")
            .setPositiveButton("Yes") { _, _ ->
                performBulkAction(isArchive)
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun performBulkAction(isArchive: Boolean) {
        pbLoading.visibility = View.VISIBLE
        var completedCount = 0
        val totalCount = selectedUsers.size

        for (user in selectedUsers) {
            val isArchivedVal = if (isShowArchivedOnly) 0 else 1
            val request = ArchiveRequest(user.userId, user.role, isArchivedVal)
            
            RetrofitClient.instance.archiveUser(request).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    completedCount++
                    if (completedCount == totalCount) {
                        pbLoading.visibility = View.GONE
                        toggleSelectionMode(false)
                        fetchUsers()
                    }
                }
                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    completedCount++
                    if (completedCount == totalCount) {
                        pbLoading.visibility = View.GONE
                        toggleSelectionMode(false)
                        fetchUsers()
                    }
                }
            })
        }
    }

    private fun showPurokBottomSheet() {
        val puroks = arrayOf(
            "All Puroks", "Purok 2", "Purok 3", "Purok 4", "Dos Riles", 
            "Sentro", "San Isidro", "Paraiso", "Riverside", "Kalaw Street", 
            "Home Subdivision", "Tanco Road / Ayala Highway", "Brixton Area"
        )
        
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.layout_purok_selection, null)
        val list = view.findViewById<ListView>(R.id.list_puroks)
        
        val adapter = object : ArrayAdapter<String>(this, R.layout.item_purok_selection, puroks) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                if (puroks[position] == selectedPurokFilter) {
                    v.setTextColor(getColor(R.color.teal_link)) // Using teal for selected
                    v.setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    v.setTextColor(Color.parseColor("#333333"))
                    v.setTypeface(null, android.graphics.Typeface.NORMAL)
                }
                return v
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            selectedPurokFilter = puroks[position]
            actvPurokFilter.setText(selectedPurokFilter)
            filterList(etSearch.text.toString())
            dialog.dismiss()
        }
        
        dialog.setContentView(view)
        dialog.show()
    }

    private fun toggleSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        selectedUsers.clear()
        layoutSelectionActions.visibility = if (enabled) View.VISIBLE else View.GONE
        tvSelectionCount.text = "0 selected"
        displayUsers(currentFilteredList)
    }

    private fun showDateSelectionDialog() {
        val options = arrayOf("Select Single Date", "Select Date Range", "Clear Filter")
        AlertDialog.Builder(this)
            .setTitle("Date Filter Type")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSingleDatePicker()
                    1 -> showDateRangePicker()
                    2 -> {
                        startDate = null
                        endDate = null
                        tvDateRange.text = "All Time"
                        filterList(etSearch.text.toString())
                    }
                }
            }
            .show()
    }

    private fun showSingleDatePicker() {
        val builder = MaterialDatePicker.Builder.datePicker()
        builder.setTitleText("Select Date")
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { selection ->
            startDate = selection
            // For single date, set end date to end of that same day
            endDate = selection
            
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            tvDateRange.text = sdf.format(Date(selection))
            
            filterList(etSearch.text.toString())
        }
        picker.show(supportFragmentManager, "single_date_picker")
    }

    private fun showDateRangePicker() {
        showCustomCalendarDialog()
    }

    private fun showCustomCalendarDialog() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.dialog_custom_calendar, null)
        dialog.setContentView(view)

        val tvMonthYear = view.findViewById<TextView>(R.id.tv_month_year)
        val rvCalendar = view.findViewById<RecyclerView>(R.id.rv_calendar)
        val btnPrev = view.findViewById<ImageButton>(R.id.btn_prev_month)
        val btnNext = view.findViewById<ImageButton>(R.id.btn_next_month)
        val btnApply = view.findViewById<Button>(R.id.btn_create_event)
        val btnCancel = view.findViewById<TextView>(R.id.btn_cancel)

        btnApply.text = "Apply Filter"

        val currentCalendar = Calendar.getInstance()
        startDate?.let { currentCalendar.timeInMillis = it }

        var tempStartDate: Calendar? = startDate?.let { Calendar.getInstance().apply { timeInMillis = it } }
        var tempEndDate: Calendar? = endDate?.let { Calendar.getInstance().apply { timeInMillis = it } }

        val updateCalendarUI = {
            val sdf = SimpleDateFormat("MMMM, yyyy", Locale.getDefault())
            tvMonthYear.text = sdf.format(currentCalendar.time)
            
            val days = generateDaysForMonth(currentCalendar)
            val adapter = rvCalendar.adapter as? CalendarAdapter
            if (adapter == null) {
                rvCalendar.layoutManager = GridLayoutManager(this, 7)
                val newAdapter = CalendarAdapter(days) { clickedDate ->
                    if (tempStartDate == null || (tempStartDate != null && tempEndDate != null)) {
                        tempStartDate = clickedDate
                        tempEndDate = null
                    } else if (clickedDate.before(tempStartDate)) {
                        tempStartDate = clickedDate
                    } else {
                        tempEndDate = clickedDate
                    }
                    (rvCalendar.adapter as CalendarAdapter).setRange(tempStartDate, tempEndDate)
                }
                newAdapter.setRange(tempStartDate, tempEndDate)
                rvCalendar.adapter = newAdapter
            } else {
                adapter.updateDays(days)
                adapter.setRange(tempStartDate, tempEndDate)
            }
        }

        btnPrev.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            updateCalendarUI()
        }

        btnNext.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            updateCalendarUI()
        }

        btnApply.setOnClickListener {
            if (tempStartDate != null) {
                startDate = tempStartDate!!.timeInMillis
                endDate = tempEndDate?.timeInMillis ?: startDate
                
                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                val startStr = sdf.format(Date(startDate!!))
                val endStr = sdf.format(Date(endDate!!))
                tvDateRange.text = if (startDate == endDate) startStr else "$startStr - $endStr"
                
                filterList(etSearch.text.toString())
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show()
            }
        }

        val btnClear = view.findViewById<Button>(R.id.btn_clear_filter)
        btnClear.setOnClickListener {
            startDate = null
            endDate = null
            tvDateRange.text = "All Time"
            filterList(etSearch.text.toString())
            dialog.dismiss()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        updateCalendarUI()
        dialog.show()
    }

    private fun generateDaysForMonth(calendar: Calendar): List<Calendar?> {
        val days = mutableListOf<Calendar?>()
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
        for (i in 0 until firstDayOfWeek) {
            days.add(null)
        }
        
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in 1..maxDay) {
            val day = cal.clone() as Calendar
            day.set(Calendar.DAY_OF_MONTH, i)
            days.add(day)
            cal.set(Calendar.DAY_OF_MONTH, i)
        }
        
        return days
    }

    private fun setupPurokDropdown() {
        val puroks = arrayOf(
            "All Puroks", "Purok 2", "Purok 3", "Purok 4", "Dos Riles", 
            "Sentro", "San Isidro", "Paraiso", "Riverside", "Kalaw Street", 
            "Home Subdivision", "Tanco Road / Ayala Highway", "Brixton Area"
        )
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, puroks)
        actvPurokFilter.setAdapter(adapter)
        
        actvPurokFilter.setOnItemClickListener { _, _, position, _ ->
            selectedPurokFilter = puroks[position]
            filterList(etSearch.text.toString())
        }
    }

    private fun fetchUsers() {
        pbLoading.visibility = View.VISIBLE
        RetrofitClient.instance.getUsers().enqueue(object : Callback<UsersResponse> {
            override fun onResponse(call: Call<UsersResponse>, response: Response<UsersResponse>) {
                pbLoading.visibility = View.GONE
                if (response.isSuccessful && response.body()?.success == true) {
                    allResidents.clear()
                    allDrivers.clear()
                    
                    response.body()?.residents?.let { allResidents.addAll(it) }
                    response.body()?.users?.let { users ->
                        // Add only drivers to the drivers list
                        allDrivers.addAll(users.filter {
                            it.role.lowercase() == "driver"
                        })
                    }
                    
                    updateTabCounts()
                    updateList(currentTab)
                } else {
                    val errorMsg = response.body()?.message ?: "HTTP Error: ${response.code()}"
                    Log.e("UserManagement", "Fetch Users Failed: $errorMsg")
                    Toast.makeText(this@UserManagementActivity, "Failed to fetch users: $errorMsg", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<UsersResponse>, t: Throwable) {
                pbLoading.visibility = View.GONE
                Log.e("UserManagement", "Network Error: ${t.message}", t)
                Toast.makeText(this@UserManagementActivity, "Network Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateTabCounts() {
        tabLayout.getTabAt(0)?.text = "Residents (${allResidents.size})"
        tabLayout.getTabAt(1)?.text = "Drivers (${allDrivers.size})"
        tabLayout.getTabAt(2)?.text = "Requests (${allRequests.size})"
    }

    private fun fetchFirebaseRequests() {
        // Checking for "registration_requests" in Firebase
        database.child("registration_requests").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allRequests.clear()
                for (reqSnapshot in snapshot.children) {
                    val user = reqSnapshot.getValue(UserData::class.java)
                    if (user != null) {
                        user.requestId = reqSnapshot.key
                        // Auto-approve residents
                        if (user.role.lowercase() == "resident") {
                            Log.d("UserManagement", "Auto-approving resident: ${user.name}")
                            approveRequest(user, isSilent = true)
                        } else {
                            allRequests.add(user)
                        }
                    }
                }
                updateTabCounts()
                if (currentTab == 2) updateList(2)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("UserManagement", "Firebase Error: ${error.message}")
            }
        })
    }

    private fun setupTabLayout() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                etSearch.text.clear()
                updateSearchHint()
                updateList(currentTab)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateSearchHint() {
        etSearch.hint = when (currentTab) {
            0 -> "Search Residents..."
            1 -> "Search Drivers..."
            2 -> "Search Requests..."
            else -> "Search..."
        }
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                filterList(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch.setOnClickListener {
            etSearch.text.clear()
        }
    }

    private fun filterList(query: String) {
        val sourceList = when (currentTab) {
            0 -> allResidents
            1 -> allDrivers
            2 -> allRequests
            else -> allResidents
        }

        var filtered = sourceList.filter { user ->
            // Search filter
            val matchesSearch = query.isEmpty() || 
                    user.name.contains(query, ignoreCase = true) || 
                    user.email.contains(query, ignoreCase = true) ||
                    (user.phone?.contains(query) ?: false)

            // Archive filter
            val matchesArchive = if (currentTab == 2) true else {
                if (isShowArchivedOnly) user.isArchived == 1 else user.isArchived == 0
            }

            // Purok filter
            val matchesPurok = if (selectedPurokFilter != "All Puroks") {
                user.purok == selectedPurokFilter
            } else true

            // Date filter
            val matchesDate = if (startDate != null && endDate != null) {
                isWithinRange(user.createdAt, startDate!!, endDate!!)
            } else true

            matchesSearch && matchesArchive && matchesPurok && matchesDate
        }

        // Apply limit of 12 for residents only when no filter is applied
        if (currentTab == 0 && query.isEmpty() && selectedPurokFilter == "All Puroks" && startDate == null) {
            filtered = filtered.take(12)
        }

        currentFilteredList = filtered.toMutableList()
        displayUsers(currentFilteredList)
    }

    private fun isWithinRange(createdAt: String?, start: Long, end: Long): Boolean {
        if (createdAt == null) return false
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = sdf.parse(createdAt) ?: return false
            // MaterialDatePicker range is start of start day to start of end day.
            // We adjust end to include the full last day.
            val endOfDay = end + (24 * 60 * 60 * 1000) - 1
            return date.time in start..endOfDay
        } catch (e: Exception) {
            return false
        }
    }

    private fun updateList(tabIndex: Int) {
        filterList(etSearch.text.toString())
    }

    private fun displayUsers(users: List<UserData>) {
        containerUsers.removeAllViews()
        
        if (users.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
        } else {
            tvEmptyState.visibility = View.GONE
            val inflater = LayoutInflater.from(this)
            
            for (user in users) {
                val view = inflater.inflate(R.layout.item_user_card, containerUsers, false)
                
                val cbSelect = view.findViewById<CheckBox>(R.id.cb_select)
                cbSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
                cbSelect.isChecked = selectedUsers.contains(user)
                cbSelect.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedUsers.add(user) else selectedUsers.remove(user)
                    tvSelectionCount.text = "${selectedUsers.size} selected"
                }

                view.findViewById<TextView>(R.id.tv_user_name).text = user.name
                view.findViewById<TextView>(R.id.tv_user_email).text = user.email
                
                val userDetailText = when (user.role.lowercase()) {
                    "resident" -> user.purok ?: "No Purok"
                    "driver" -> "License: ${user.licenseNumber ?: "N/A"} | Truck: ${user.preferredTruck ?: "N/A"}"
                    else -> user.role
                }
                view.findViewById<TextView>(R.id.tv_user_detail).text = userDetailText

                val btnArchive = view.findViewById<ImageButton>(R.id.btn_archive)

                if (currentTab == 2 || isSelectionMode) {
                    btnArchive.visibility = View.GONE
                } else {
                    btnArchive.visibility = View.VISIBLE
                    btnArchive.setImageResource(if (user.isArchived == 1) android.R.drawable.ic_menu_revert else android.R.drawable.ic_menu_save)
                    btnArchive.setOnClickListener {
                        toggleArchive(user)
                    }
                }
                
                val indicator = view.findViewById<View>(R.id.view_role_indicator)
                when (user.role.lowercase()) {
                    "driver" -> indicator.setBackgroundResource(R.drawable.driver_icon_bg)
                    "admin" -> indicator.setBackgroundResource(R.drawable.driver_icon_bg)
                    else -> indicator.setBackgroundResource(R.drawable.resident_icon_bg)
                }

                val layoutActions = view.findViewById<LinearLayout>(R.id.layout_actions)
                val ivNextView = view.findViewById<View>(R.id.iv_next)

                if (currentTab == 2) {
                    layoutActions.visibility = View.VISIBLE
                    ivNextView.visibility = View.GONE
                    
                    view.findViewById<Button>(R.id.btn_approve).setOnClickListener {
                        showConfirmDialog(user, true)
                    }
                    view.findViewById<Button>(R.id.btn_decline).setOnClickListener {
                        showConfirmDialog(user, false)
                    }
                } else {
                    layoutActions.visibility = View.GONE
                    ivNextView.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
                    view.setOnClickListener {
                        if (isSelectionMode) {
                            cbSelect.isChecked = !cbSelect.isChecked
                        } else {
                            // Details logic for residents/drivers
                        }
                    }
                }
                
                containerUsers.addView(view)
            }
        }
    }

    private fun showConfirmDialog(user: UserData, isApprove: Boolean) {
        val title = if (isApprove) "Approve Request" else "Decline Request"
        val message = if (isApprove) 
            "Are you sure you want to approve ${user.name}?" 
            else "Are you sure you want to decline ${user.name}'s request?"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Yes") { _, _ ->
                if (isApprove) approveRequest(user) else declineRequest(user)
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showRequestActionDialog(user: UserData) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_request_action, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tv_dialog_message).text = "Action for ${user.name}?"
        dialogView.findViewById<TextView>(R.id.tv_dialog_email).text = "Email: ${user.email}"
        dialogView.findViewById<TextView>(R.id.tv_dialog_phone).text = "Phone: ${user.phone ?: "N/A"}"
        dialogView.findViewById<TextView>(R.id.tv_dialog_role_info).text = "Role: ${user.role.uppercase()}"

        dialogView.findViewById<Button>(R.id.btn_approve).setOnClickListener {
            approveRequest(user)
            alertDialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_decline).setOnClickListener {
            declineRequest(user)
            alertDialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

    private fun toggleArchive(user: UserData) {
        val newStatus = if (user.isArchived == 1) 0 else 1
        val action = if (newStatus == 1) "archive" else "unarchive"
        
        val roleName = user.role.lowercase()
        val explanation = if (action == "archive") {
            "\n\nArchived users will no longer be able to log in, and their data will be hidden. " +
            (if (roleName == "driver") "Drivers will also be automatically removed from the tracking map." else "")
        } else {
            "\n\nThis will restore the user's access and they will be able to log in again."
        }

        AlertDialog.Builder(this)
            .setTitle("Confirm ${action.replaceFirstChar { it.uppercase() }}")
            .setMessage("Are you sure you want to $action this $roleName?$explanation")
            .setPositiveButton("Yes") { _, _ ->
                pbLoading.visibility = View.VISIBLE
                val request = ArchiveRequest(user.userId, user.role, newStatus)
                RetrofitClient.instance.archiveUser(request).enqueue(object : Callback<ApiResponse> {
                    override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                        if (response.isSuccessful && response.body()?.success == true) {
                            Toast.makeText(this@UserManagementActivity, response.body()?.message, Toast.LENGTH_SHORT).show()
                            fetchUsers()
                        } else {
                            pbLoading.visibility = View.GONE
                            Toast.makeText(this@UserManagementActivity, "Action failed", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                        pbLoading.visibility = View.GONE
                        Toast.makeText(this@UserManagementActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun approveRequest(user: UserData, isSilent: Boolean = false) {
        val registerRequest = RegisterRequest(
            username = user.username,
            name = user.name,
            email = user.email,
            password = user.password ?: "",
            role = user.role,
            phone = user.phone,
            purok = user.purok,
            completeAddress = user.completeAddress,
            licenseNumber = user.licenseNumber,
            preferredTruck = user.preferredTruck
        )

        if (!isSilent) pbLoading.visibility = View.VISIBLE
        RetrofitClient.instance.register(registerRequest).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                if (response.isSuccessful && response.body()?.success == true) {
                    user.requestId?.let {
                        database.child("registration_requests").child(it).removeValue()
                    }
                    if (!isSilent) {
                        Toast.makeText(this@UserManagementActivity, "User Approved!", Toast.LENGTH_SHORT).show()
                    }
                    fetchUsers() // Refresh list
                } else {
                    if (!isSilent) {
                        pbLoading.visibility = View.GONE
                        val errorMsg = response.body()?.message ?: "Approval failed"
                        Toast.makeText(this@UserManagementActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                if (!isSilent) {
                    pbLoading.visibility = View.GONE
                    Toast.makeText(this@UserManagementActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun declineRequest(user: UserData) {
        user.requestId?.let {
            database.child("registration_requests").child(it).removeValue()
                .addOnSuccessListener {
                    Toast.makeText(this, "Request Declined", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_monitor

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_monitor -> true
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
                R.id.nav_settings -> {
                    startActivity(Intent(this, AdminSettingsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}
