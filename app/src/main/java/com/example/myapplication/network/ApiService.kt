package com.example.myapplication.network

import com.example.myapplication.models.*
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded

interface ApiService {
    @POST("register.php")
    fun register(@Body request: RegisterRequest): Call<ApiResponse>

    @POST("login.php")
    fun login(@Body request: LoginRequest): Call<ApiResponse>

    @GET("get_users.php")
    fun getUsers(): Call<UsersResponse>

    @GET("get_complaints.php")
    fun getComplaints(): Call<ComplaintsResponse>

    @FormUrlEncoded
    @POST("update_complaint.php")
    fun updateComplaint(
        @Field("complaint_id") id: Int,
        @Field("status") status: String,
        @Field("admin_response") response: String?
    ): Call<ApiResponse>

    @FormUrlEncoded
    @POST("file_complaint.php")
    fun fileComplaint(
        @Field("resident_id") residentId: String,
        @Field("category") category: String,
        @Field("description") description: String
    ): Call<ApiResponse>

    @FormUrlEncoded
    @POST("delete_complaint.php")
    fun deleteComplaint(
        @Field("complaint_id") id: Int,
        @Field("action") action: String
    ): Call<ApiResponse>

    @FormUrlEncoded
    @POST("bulk_delete_complaints.php")
    fun bulkDeleteComplaints(
        @Field("complaint_ids") ids: String
    ): Call<ApiResponse>

    @FormUrlEncoded
    @POST("update_location.php")
    fun updateLocation(
        @Field("user_id") userId: Int,
        @Field("latitude") latitude: Double,
        @Field("longitude") longitude: Double,
        @Field("truck_id") truckId: String,
        @Field("speed") speed: Double,
        @Field("status") status: String,
        @Field("is_full") isFull: Boolean
    ): Call<ApiResponse>

    @FormUrlEncoded
    @POST("log_collection.php")
    fun logCollection(
        @Field("truck_id") truckId: String,
        @Field("zone_name") zoneName: String,
        @Field("type") type: String
    ): Call<ApiResponse>

    @GET("get_locations.php")
    fun getLocations(): Call<LocationsResponse>

    @POST("forgot_password.php")
    fun forgotPassword(@Body request: ForgotPasswordRequest): Call<ApiResponse>

    @POST("verify_otp.php")
    fun verifyOtp(@Body request: VerifyOtpRequest): Call<ApiResponse>

    @POST("reset_password_final.php")
    fun resetPasswordFinal(@Body request: ResetPasswordFinalRequest): Call<ApiResponse>

    @FormUrlEncoded
    @POST("update_resident_profile.php")
    fun updateResidentProfile(
        @Field("user_id") userId: Int,
        @Field("name") name: String,
        @Field("email") email: String,
        @Field("phone") phone: String,
        @Field("purok") purok: String
    ): Call<ApiResponse>

    @FormUrlEncoded
    @POST("change_password.php")
    fun changePassword(
        @Field("id") id: Int,
        @Field("role") role: String,
        @Field("old_password") oldPass: String,
        @Field("new_password") newPass: String
    ): Call<ApiResponse>

    @POST("archive_user.php")
    fun archiveUser(@Body request: ArchiveRequest): Call<ApiResponse>

    @GET("trigger_backup.php")
    fun triggerBackup(): Call<ApiResponse>

    @GET("get_backup_history.php")
    fun getBackupHistory(): Call<BackupHistoryResponse>

    @GET("delete_backup.php")
    fun deleteBackup(@retrofit2.http.Query("filename") filename: String): Call<ApiResponse>
}
