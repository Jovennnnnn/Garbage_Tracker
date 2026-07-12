package com.example.myapplication

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import com.example.myapplication.utils.CustomNotification
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class DriverRegisterActivity : AppCompatActivity() {

    private lateinit var tilUsername: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var etUsername: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: com.google.android.material.textfield.TextInputEditText
    private lateinit var etConfirmPassword: com.google.android.material.textfield.TextInputEditText
    private lateinit var etFullName: EditText
    private lateinit var etLicenseNumber: EditText
    private lateinit var etContactNumber: EditText
    private lateinit var etTruckAssignment: EditText
    private lateinit var btnSubmit: View
    private lateinit var pbRegister: ProgressBar
    private lateinit var tvSubmitText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_driver_register)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.driver_register_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupValidation()

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_cancel).setOnClickListener { finish() }
        btnSubmit.setOnClickListener { submitRequest() }
    }

    private fun initViews() {
        tilUsername = findViewById(R.id.til_username)
        tilEmail = findViewById(R.id.til_email)
        tilPassword = findViewById(R.id.til_password)
        tilConfirmPassword = findViewById(R.id.til_confirm_password)
        etUsername = findViewById(R.id.et_username)
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        etConfirmPassword = findViewById(R.id.et_confirm_password)
        etFullName = findViewById(R.id.et_full_name)
        etLicenseNumber = findViewById(R.id.et_license_number)
        etContactNumber = findViewById(R.id.et_contact_number)
        etTruckAssignment = findViewById(R.id.et_truck_assignment)
        btnSubmit = findViewById(R.id.btn_submit)
        pbRegister = findViewById(R.id.pb_register)
        tvSubmitText = findViewById(R.id.tv_submit_text)
    }

    private fun setupValidation() {
        etUsername.addTextChangedListener { tilUsername.error = null }
        
        etEmail.addTextChangedListener { text ->
            val email = text.toString().trim()
            if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.error = "Please use a valid email address"
                tilEmail.isHelperTextEnabled = false
            } else {
                tilEmail.error = null
                tilEmail.isHelperTextEnabled = true
            }
        }

        etPassword.addTextChangedListener { text ->
            val pass = text.toString()
            if (pass.isNotEmpty() && pass.length < 8) {
                tilPassword.error = "At least 8 characters required"
                tilPassword.isHelperTextEnabled = false
            } else {
                tilPassword.error = null
                tilPassword.isHelperTextEnabled = true
            }
            validatePasswordMatch()
        }

        etConfirmPassword.addTextChangedListener {
            validatePasswordMatch()
        }
    }

    private fun validatePasswordMatch() {
        val pass = etPassword.text.toString()
        val confirmPass = etConfirmPassword.text.toString()
        if (confirmPass.isNotEmpty() && pass != confirmPass) {
            tilConfirmPassword.error = "Passwords do not match"
        } else {
            tilConfirmPassword.error = null
        }
    }

    private fun submitRequest() {
        val username = etUsername.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()
        val fullName = etFullName.text.toString().trim()
        val contactNumber = etContactNumber.text.toString().trim()
        val licenseNumber = etLicenseNumber.text.toString().trim()
        val truckAssignment = etTruckAssignment.text.toString().trim()

        var hasError = false

        if (username.isEmpty()) {
            tilUsername.error = "Username is required"
            hasError = true
        }

        if (email.isEmpty()) {
            tilEmail.error = "Email is required"
            hasError = true
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "Invalid email format"
            hasError = true
        }

        if (password.isEmpty()) {
            tilPassword.error = "Password is required"
            hasError = true
        } else if (password.length < 8) {
            tilPassword.error = "Too short"
            hasError = true
        }

        if (password != confirmPassword) {
            tilConfirmPassword.error = "Passwords do not match"
            hasError = true
        }

        if (fullName.isEmpty() || contactNumber.isEmpty() || licenseNumber.isEmpty()) {
            CustomNotification.showTopNotification(this, "Please fill in all required fields")
            hasError = true
        }

        if (hasError) return

        showLoading(true)

        val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
        val database = FirebaseDatabase.getInstance(dbUrl).getReference("registration_requests")
        val requestId = database.push().key ?: System.currentTimeMillis().toString()

        val requestData = mapOf(
            "userId" to 0,
            "username" to username,
            "name" to fullName,
            "email" to email,
            "password" to password,
            "role" to "driver",
            "phone" to contactNumber,
            "licenseNumber" to licenseNumber,
            "preferredTruck" to truckAssignment,
            "status" to "pending",
            "timestamp" to ServerValue.TIMESTAMP
        )

        database.child(requestId).setValue(requestData)
            .addOnSuccessListener {
                // Send system notification to Admin
                val notification = com.example.myapplication.models.SystemNotification(
                    type = "REGISTRATION",
                    title = "New Driver Registration",
                    message = "$fullName is requesting registration as a driver.",
                    timestamp = System.currentTimeMillis(),
                    isRead = false,
                    relatedId = requestId
                )
                val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
                com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                    .getReference("notifications").push().setValue(notification)

                showLoading(false)
                CustomNotification.showTopNotification(this, "Driver Application Sent! Wait for Admin Approval.", false)
                Handler(Looper.getMainLooper()).postDelayed({ finish() }, 2000)
            }
            .addOnFailureListener {
                showLoading(false)
                CustomNotification.showTopNotification(this, "Failed to send request: ${it.message}")
            }
    }

    private fun showLoading(show: Boolean) {
        pbRegister.visibility = if (show) View.VISIBLE else View.GONE
        tvSubmitText.text = if (show) "Submitting..." else "Register as Driver"
        btnSubmit.isEnabled = !show
    }
}
