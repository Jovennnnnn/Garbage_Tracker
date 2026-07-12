package com.example.myapplication

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import com.example.myapplication.models.ApiResponse
import com.example.myapplication.models.RegisterRequest
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.utils.CustomNotification
import com.example.myapplication.utils.SessionManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ResidentRegisterActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var tilUsername: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var etUsername: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var etFullName: EditText
    private lateinit var etContactNumber: EditText
    private lateinit var spinnerPurok: Spinner
    private lateinit var etAddress: EditText
    private lateinit var btnSubmit: View
    private lateinit var pbRegister: ProgressBar
    private lateinit var tvSubmitText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_resident_register)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.resident_register_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupValidation()
        setupPurokSpinner()

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_cancel).setOnClickListener { finish() }
        btnSubmit.setOnClickListener { performRegistration() }
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
        etContactNumber = findViewById(R.id.et_contact_number)
        spinnerPurok = findViewById(R.id.spinner_purok)
        etAddress = findViewById(R.id.et_address)
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

    private fun setupPurokSpinner() {
        val puroks = arrayOf(
            "Select Purok", "Purok 2", "Purok 3", "Purok 4", "Dos Riles", "Sentro",
            "San Isidro", "Paraiso", "Riverside", "Kalaw Street",
            "Home Subdivision", "Tanco Road / Ayala Highway", "Brixton Area"
        )
        val adapter = ArrayAdapter(this, R.layout.spinner_item, puroks)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerPurok.adapter = adapter
    }

    private fun performRegistration() {
        val username = etUsername.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()
        val fullName = etFullName.text.toString().trim()
        val phone = etContactNumber.text.toString().trim()
        val purok = spinnerPurok.selectedItem.toString()
        val address = etAddress.text.toString().trim()

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

        if (fullName.isEmpty() || phone.isEmpty() || address.isEmpty() || spinnerPurok.selectedItemPosition == 0) {
            CustomNotification.showTopNotification(this, "Please fill all fields")
            hasError = true
        }

        if (hasError) return

        showLoading(true)

        val request = RegisterRequest(
            username = username,
            name = fullName,
            email = email,
            password = password,
            role = "resident",
            phone = phone,
            purok = purok,
            completeAddress = address
        )

        RetrofitClient.instance.register(request).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                showLoading(false)
                if (response.isSuccessful && response.body()?.success == true) {
                    // Notify Admin
                    val notification = com.example.myapplication.models.SystemNotification(
                        type = "REGISTRATION",
                        title = "New Registration Request",
                        message = "$fullName has requested to join as a Resident in $purok",
                        timestamp = System.currentTimeMillis(),
                        isRead = false,
                        relatedId = username
                    )
                    val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
                    com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                        .getReference("notifications").push().setValue(notification)

                    CustomNotification.showTopNotification(this@ResidentRegisterActivity, "Registration Successful!", false)
                    showNotificationConsentDialog()
                } else {
                    val msg = response.body()?.message ?: "Registration Failed"
                    CustomNotification.showTopNotification(this@ResidentRegisterActivity, msg)
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                showLoading(false)
                CustomNotification.showTopNotification(this@ResidentRegisterActivity, "Connection Error")
            }
        })
    }

    private fun showNotificationConsentDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_notification_consent, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.btn_enable_notifications).setOnClickListener {
            sessionManager.setAppNotificationsEnabled(true)
            dialog.dismiss()
            finish()
        }

        dialogView.findViewById<View>(R.id.btn_maybe_later).setOnClickListener {
            sessionManager.setAppNotificationsEnabled(false)
            dialog.dismiss()
            finish()
        }

        dialog.show()
    }

    private fun showLoading(show: Boolean) {
        pbRegister.visibility = if (show) View.VISIBLE else View.GONE
        tvSubmitText.text = if (show) "Submitting..." else "Submit Registration"
        btnSubmit.isEnabled = !show
    }
}
