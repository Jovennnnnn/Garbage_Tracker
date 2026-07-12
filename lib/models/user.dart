class UserData {
  final int userId;
  final String username;
  final String name;
  final String email;
  final String role;
  final String? phone;
  final String? purok;
  final String? completeAddress;
  final String? licenseNumber;
  final String? preferredTruck;
  final int isArchived;
  final String? createdAt;

  UserData({
    this.userId = 0,
    this.username = '',
    this.name = '',
    this.email = '',
    this.role = '',
    this.phone,
    this.purok,
    this.completeAddress,
    this.licenseNumber,
    this.preferredTruck,
    this.isArchived = 0,
    this.createdAt,
  });

  factory UserData.fromJson(Map<String, dynamic> json) {
    return UserData(
      userId: json['user_id'] ?? 0,
      username: json['username'] ?? '',
      name: json['name'] ?? '',
      email: json['email'] ?? '',
      role: json['role'] ?? '',
      phone: json['phone'],
      purok: json['purok'],
      completeAddress: json['complete_address'],
      licenseNumber: json['license_number'],
      preferredTruck: json['preferred_truck'],
      isArchived: json['is_archived'] ?? 0,
      createdAt: json['created_at'],
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'user_id': userId,
      'username': username,
      'name': name,
      'email': email,
      'role': role,
      'phone': phone,
      'purok': purok,
      'complete_address': completeAddress,
      'license_number': licenseNumber,
      'preferred_truck': preferredTruck,
      'is_archived': isArchived,
      'created_at': createdAt,
    };
  }
}
