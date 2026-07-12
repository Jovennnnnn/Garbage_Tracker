import 'package:dio/dio.dart';
import 'api_client.dart';

class ApiService {
  final Dio _dio = ApiClient.instance;

  Future<Response> login(String username, String password) async {
    return await _dio.post('login.php', data: {
      'username_or_email': username,
      'password': password,
    });
  }

  Future<Response> register(Map<String, dynamic> data) async {
    return await _dio.post('register.php', data: data);
  }

  Future<Response> getUsers() async {
    return await _dio.get('get_users.php');
  }

  Future<Response> approveUser(int id, String role) async {
    return await _dio.post('approve_user.php', data: {
      'user_id': id,
      'role': role,
    });
  }

  Future<Response> rejectUser(int id, String role) async {
    return await _dio.post('reject_user.php', data: {
      'user_id': id,
      'role': role,
    });
  }

  Future<Response> getComplaints() async {
    return await _dio.get('get_complaints.php');
  }

  Future<Response> updateComplaint(int id, String status, String? response) async {
    return await _dio.post('update_complaint.php', data: FormData.fromMap({
      'complaint_id': id,
      'status': status,
      'admin_response': response,
    }));
  }

  Future<Response> fileComplaint(String residentId, String category, String description) async {
    return await _dio.post('file_complaint.php', data: FormData.fromMap({
      'resident_id': residentId,
      'category': category,
      'description': description,
    }));
  }

  Future<Response> getLocations() async {
    return await _dio.get('get_locations.php');
  }

  Future<Response> updateLocation({
    required int userId,
    required double latitude,
    required double longitude,
    required String truckId,
    required double speed,
    required String status,
    required bool isFull,
  }) async {
    return await _dio.post('update_location.php', data: FormData.fromMap({
      'user_id': userId,
      'latitude': latitude,
      'longitude': longitude,
      'truck_id': truckId,
      'speed': speed,
      'status': status,
      'is_full': isFull,
    }));
  }

  Future<Response> changePassword(int id, String role, String oldPass, String newPass) async {
    return await _dio.post('change_password.php', data: FormData.fromMap({
      'id': id,
      'role': role,
      'old_password': oldPass,
      'new_password': newPass,
    }));
  }

  Future<Response> forgotPassword(String email) async {
    return await _dio.post('forgot_password.php', data: {
      'email': email,
    });
  }

  Future<Response> verifyOTP(String email, String otp) async {
    return await _dio.post('verify_otp.php', data: {
      'email': email,
      'otp': otp,
    });
  }

  Future<Response> resetPassword(String email, String otp, String password) async {
    return await _dio.post('reset_password_final.php', data: {
      'email': email,
      'otp': otp,
      'password': password,
    });
  }

  Future<Response> checkEmail(String email) async {
    return await _dio.post('check_email.php', data: FormData.fromMap({
      'email': email,
    }));
  }
}
