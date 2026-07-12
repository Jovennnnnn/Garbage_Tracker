package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.models.*
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.utils.CustomNotification
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var layoutStep1: LinearLayout
    private lateinit var layoutStep2: LinearLayout
    private lateinit var layoutStep3: LinearLayout
    
    private lateinit var pbStep1: ProgressBar
    private lateinit var pbStep2: ProgressBar
    private lateinit var pbStep3: ProgressBar

    private lateinit var stepIcon: ImageView
    private lateinit var stepTitle: TextView
    private lateinit var stepSubtitle: TextView

    private var currentEmail: String = ""
    private var currentOtp: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_forgot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        layoutStep1 = findViewById(R.id.layout_step1)
        layoutStep2 = findViewById(R.id.layout_step2)
        layoutStep3 = findViewById(R.id.layout_step3)
        
        pbStep1 = findViewById(R.id.pb_step1)
        pbStep2 = findViewById(R.id.pb_step2)
        pbStep3 = findViewById(R.id.pb_step3)

        stepIcon = findViewById(R.id.step_icon)
        stepTitle = findViewById(R.id.step_title)
        stepSubtitle = findViewById(R.id.step_subtitle)

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // Step 1: Send OTP
        findViewById<View>(R.id.btn_send_token).setOnClickListener {
            handleStep1()
        }

        // Step 2: Verify OTP
        findViewById<View>(R.id.btn_verify_token).setOnClickListener {
            handleStep2()
        }

        // Step 3: Reset Password
        findViewById<View>(R.id.btn_submit_reset).setOnClickListener {
            handleStep3()
        }

        setupPasswordVisibilityToggles()
        
        updateHeader(1)
    }

    private fun setupPasswordVisibilityToggles() {
        val etNewPassword = findViewById<EditText>(R.id.et_new_password)
        val ivShowNewPassword = findViewById<ImageView>(R.id.iv_show_new_password)
        var isNewPasswordVisible = false

        ivShowNewPassword.setOnClickListener {
            if (isNewPasswordVisible) {
                etNewPassword.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                ivShowNewPassword.setImageResource(R.drawable.ic_eye_off)
            } else {
                etNewPassword.transformationMethod = android.text.method.HideReturnsTransformationMethod.getInstance()
                ivShowNewPassword.setImageResource(R.drawable.ic_eye)
            }
            isNewPasswordVisible = !isNewPasswordVisible
            etNewPassword.setSelection(etNewPassword.text.length)
        }

        val etConfirmPassword = findViewById<EditText>(R.id.et_confirm_password)
        val ivShowConfirmPassword = findViewById<ImageView>(R.id.iv_show_confirm_password)
        var isConfirmPasswordVisible = false

        ivShowConfirmPassword.setOnClickListener {
            if (isConfirmPasswordVisible) {
                etConfirmPassword.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                ivShowConfirmPassword.setImageResource(R.drawable.ic_eye_off)
            } else {
                etConfirmPassword.transformationMethod = android.text.method.HideReturnsTransformationMethod.getInstance()
                ivShowConfirmPassword.setImageResource(R.drawable.ic_eye)
            }
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            etConfirmPassword.setSelection(etConfirmPassword.text.length)
        }
    }

    private fun updateHeader(step: Int) {
        stepIcon.setImageResource(android.R.drawable.ic_lock_idle_lock)
        
        when(step) {
            1 -> {
                stepTitle.text = "Verify Email"
                stepSubtitle.text = "Step 1 of 3"
            }
            2 -> {
                stepTitle.text = "Enter OTP"
                stepSubtitle.text = "Step 2 of 3"
            }
            3 -> {
                stepTitle.text = "Reset Password"
                stepSubtitle.text = "Step 3 of 3"
            }
        }
        stepIcon.scaleX = 0.5f
        stepIcon.scaleY = 0.5f
        stepIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start()
    }

    private fun handleStep1() {
        val email = findViewById<EditText>(R.id.et_email).text.toString().trim()

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            CustomNotification.showTopNotification(this, "Please enter a valid email address")
            return
        }

        showLoading(1, true)
        val request = ForgotPasswordRequest(email)
        RetrofitClient.instance.forgotPassword(request).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                showLoading(1, false)
                if (response.isSuccessful && response.body()?.success == true) {
                    currentEmail = email
                    CustomNotification.showTopNotification(this@ForgotPasswordActivity, "OTP sent to your email", false)
                    layoutStep1.visibility = View.GONE
                    layoutStep2.visibility = View.VISIBLE
                    updateHeader(2)
                } else {
                    val message = response.body()?.message ?: "Email not found"
                    CustomNotification.showTopNotification(this@ForgotPasswordActivity, message)
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                showLoading(1, false)
                CustomNotification.showTopNotification(this@ForgotPasswordActivity, "Network Error")
            }
        })
    }

    private fun handleStep2() {
        val otp = findViewById<EditText>(R.id.et_token).text.toString().trim()

        if (otp.isEmpty() || otp.length != 6) {
            CustomNotification.showTopNotification(this, "Please enter the 6-digit OTP code")
            return
        }

        showLoading(2, true)
        val request = VerifyOtpRequest(currentEmail, otp)
        RetrofitClient.instance.verifyOtp(request).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                showLoading(2, false)
                if (response.isSuccessful && response.body()?.success == true) {
                    currentOtp = otp
                    CustomNotification.showTopNotification(this@ForgotPasswordActivity, "OTP verified", false)
                    layoutStep2.visibility = View.GONE
                    layoutStep3.visibility = View.VISIBLE
                    updateHeader(3)
                } else {
                    val message = response.body()?.message ?: "Invalid or expired OTP"
                    CustomNotification.showTopNotification(this@ForgotPasswordActivity, message)
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                showLoading(2, false)
                CustomNotification.showTopNotification(this@ForgotPasswordActivity, "Network Error")
            }
        })
    }

    private fun handleStep3() {
        val newPass = findViewById<EditText>(R.id.et_new_password).text.toString()
        val confirmPass = findViewById<EditText>(R.id.et_confirm_password).text.toString()

        val passwordPattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$".toRegex()

        if (!newPass.matches(passwordPattern)) {
            CustomNotification.showTopNotification(this, "Password must be 8+ chars with Uppercase, Lowercase, Number, and Symbol")
            return
        }

        if (newPass != confirmPass) {
            CustomNotification.showTopNotification(this, "Passwords do not match")
            return
        }

        showLoading(3, true)
        val request = ResetPasswordFinalRequest(currentEmail, currentOtp, newPass)
        RetrofitClient.instance.resetPasswordFinal(request).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                showLoading(3, false)
                if (response.isSuccessful && response.body()?.success == true) {
                    CustomNotification.showTopNotification(this@ForgotPasswordActivity, "Password updated successfully", false)
                    Handler(Looper.getMainLooper()).postDelayed({
                        finish()
                    }, 1500)
                } else {
                    val message = response.body()?.message ?: "Reset failed"
                    CustomNotification.showTopNotification(this@ForgotPasswordActivity, message)
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                showLoading(3, false)
                CustomNotification.showTopNotification(this@ForgotPasswordActivity, "Update Error")
            }
        })
    }

    private fun showLoading(step: Int, show: Boolean) {
        when(step) {
            1 -> {
                pbStep1.visibility = if (show) View.VISIBLE else View.GONE
                findViewById<View>(R.id.btn_send_token).isEnabled = !show
            }
            2 -> {
                pbStep2.visibility = if (show) View.VISIBLE else View.GONE
                findViewById<View>(R.id.btn_verify_token).isEnabled = !show
            }
            3 -> {
                pbStep3.visibility = if (show) View.VISIBLE else View.GONE
                findViewById<View>(R.id.btn_submit_reset).isEnabled = !show
            }
        }
    }
}
