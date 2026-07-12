import 'package:flutter/material.dart';
import '../utils/session_manager.dart';

class AdminSettingsScreen extends StatefulWidget {
  final bool isEmbedded;
  final VoidCallback? onBack;
  const AdminSettingsScreen({super.key, this.isEmbedded = false, this.onBack});

  @override
  State<AdminSettingsScreen> createState() => _AdminSettingsScreenState();
}

class _AdminSettingsScreenState extends State<AdminSettingsScreen> {
  bool _emailNotifications = true;
  bool _appNotifications = true;
  bool _autoBackup = true;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(),
            Expanded(
              child: SingleChildScrollView(
                physics: const BouncingScrollPhysics(),
                padding: const EdgeInsets.only(bottom: 40),
                child: Column(
                  children: [
                    const SizedBox(height: 24),
                    _buildProfileSection(),
                    _buildNotificationSection(),
                    _buildDataManagementSection(),
                    _buildSecuritySection(),
                    const SizedBox(height: 24),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 20),
                      child: ElevatedButton.icon(
                        onPressed: () => _showLogoutDialog(context),
                        icon: const Icon(Icons.logout_rounded, color: Colors.white),
                        label: const Text("Sign Out from Admin Panel", style: TextStyle(color: Colors.white, fontWeight: FontWeight.w900, fontSize: 16)),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFF00BFA5),
                          minimumSize: const Size(double.infinity, 60),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                          elevation: 0,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      color: Colors.white,
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
      child: Row(
        children: [
          if (!widget.isEmbedded || widget.onBack != null) ...[
            IconButton(
              icon: const Icon(Icons.arrow_back, color: Color(0xFF1A1A1A)),
              onPressed: () {
                if (widget.onBack != null) {
                  widget.onBack!();
                } else {
                  Navigator.pop(context);
                }
              },
            ),
            const SizedBox(width: 8),
          ],
          const Expanded(
            child: Text("Admin Settings", style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
          ),
        ],
      ),
    );
  }

  Widget _buildProfileSection() {
    return _buildSectionCard(Icons.person_rounded, "Administrator Profile", [
      _buildProfileItem("Administrator", "System Admin"),
      _buildProfileItem("Email", "princebarola191@gmail.com"),
      _buildProfileItem("Contact", "09171234567"),
    ]);
  }

  Widget _buildNotificationSection() {
    return _buildSectionCard(Icons.notifications_rounded, "System Notifications", [
      _buildSwitchRow("Email Notifications", "Receive system alerts via email", _emailNotifications, (v) => setState(() => _emailNotifications = v)),
      _buildSwitchRow("App Notifications", "System alerts and updates", _appNotifications, (v) => setState(() => _appNotifications = v)),
    ]);
  }

  Widget _buildDataManagementSection() {
    return _buildSectionCard(Icons.file_download_outlined, "Data Management", [
      _buildSwitchRow("Auto Backup", "Daily automatic backups", _autoBackup, (v) => setState(() => _autoBackup = v)),
      const SizedBox(height: 12),
      Row(
        children: [
          Expanded(child: _buildActionLink("Backup Now", "Create manual backup", const Color(0xFF1E88E5), () {})),
          const Icon(Icons.chevron_right_rounded, color: Color(0xFF1E88E5), size: 18),
        ],
      ),
      Align(
        alignment: Alignment.centerRight,
        child: TextButton(onPressed: () {}, child: const Text("View History", style: TextStyle(color: Color(0xFF1E88E5), fontWeight: FontWeight.w900, fontSize: 13))),
      ),
      _buildActionLink("Export Data", "Download system data (PDF)", const Color(0xFF9C27B0), () {}),
    ]);
  }

  Widget _buildSecuritySection() {
    return _buildSectionCard(Icons.lock_rounded, "Security", [
      _buildSecurityRow("Change Password", () => _showChangePasswordDialog(context)),
      const Padding(padding: EdgeInsets.symmetric(horizontal: 12), child: Divider(height: 1, color: Color(0xFFF5F5F5))),
      _buildSecurityRow("Two-Factor Authentication", () => _show2FADialog(context)),
      const Padding(padding: EdgeInsets.symmetric(horizontal: 12), child: Divider(height: 1, color: Color(0xFFF5F5F5))),
      _buildSecurityRow("Access Logs", () => _showAccessLogsDialog(context)),
      const Padding(padding: EdgeInsets.symmetric(horizontal: 12), child: Divider(height: 1, color: Color(0xFFF5F5F5))),
      _buildSecurityRow("User Permissions", () => _showPermissionsDialog(context)),
    ]);
  }

  // --- Modals (Matching Images 3, 4, 5, 6) ---

  void _showChangePasswordDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(32)),
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  const Icon(Icons.lock_outline_rounded, color: Color(0xFF2E7D32), size: 28),
                  const SizedBox(width: 12),
                  const Text("Change Password", style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900, color: Color(0xFF2E7D32))),
                ],
              ),
              const SizedBox(height: 12),
              const Text("Ensure your account is using a long, random password to stay secure.", style: TextStyle(color: Color(0xFF757575), fontSize: 13, fontWeight: FontWeight.w500)),
              const SizedBox(height: 32),
              _dialogTextField("Current Password"),
              const SizedBox(height: 16),
              _dialogTextField("New Password"),
              const SizedBox(height: 16),
              _dialogTextField("Confirm New Password"),
              const SizedBox(height: 40),
              ElevatedButton(
                onPressed: () => Navigator.pop(context),
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF2E7D32),
                  minimumSize: const Size(double.infinity, 56),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  elevation: 0,
                ),
                child: const Text("Update Password", style: TextStyle(color: Colors.white, fontWeight: FontWeight.w900)),
              ),
              const SizedBox(height: 12),
              TextButton(onPressed: () => Navigator.pop(context), child: const Text("CANCEL", style: TextStyle(color: Color(0xFF757575), fontWeight: FontWeight.w900))),
            ],
          ),
        ),
      ),
    );
  }

  void _show2FADialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(32)),
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  const Icon(Icons.lock_outline_rounded, color: Color(0xFF2E7D32), size: 28),
                  const SizedBox(width: 12),
                  const Text("Two-Factor Authentication", style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900, color: Color(0xFF2E7D32))),
                ],
              ),
              const SizedBox(height: 12),
              const Text("Add an extra layer of security to your account.", style: TextStyle(color: Color(0xFF757575), fontSize: 13, fontWeight: FontWeight.w500)),
              const SizedBox(height: 32),
              Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(color: const Color(0xFFF1F8E9), borderRadius: BorderRadius.circular(20)),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text("Email Verification", style: TextStyle(fontWeight: FontWeight.w800, color: Color(0xFF1A1A1A))),
                          Text("Verification codes sent to admin@balintawak.gov", style: TextStyle(fontSize: 11, color: Colors.grey[600])),
                        ],
                      ),
                    ),
                    Switch(value: true, onChanged: (v) {}, activeColor: const Color(0xFF00BFA5)),
                  ],
                ),
              ),
              const SizedBox(height: 32),
              ElevatedButton(
                onPressed: () => Navigator.pop(context),
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF2E7D32),
                  minimumSize: const Size(double.infinity, 56),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  elevation: 0,
                ),
                child: const Text("Setup Authenticator App", style: TextStyle(color: Colors.white, fontWeight: FontWeight.w900)),
              ),
              const SizedBox(height: 12),
              TextButton(onPressed: () => Navigator.pop(context), child: const Text("CLOSE", style: TextStyle(color: Color(0xFF757575), fontWeight: FontWeight.w900))),
            ],
          ),
        ),
      ),
    );
  }

  void _showAccessLogsDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(32)),
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  const Icon(Icons.access_time_rounded, color: Color(0xFF2E7D32), size: 28),
                  const SizedBox(width: 12),
                  const Text("System Access Logs", style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900, color: Color(0xFF2E7D32))),
                ],
              ),
              const SizedBox(height: 12),
              const Text("Recent security activity and login history", style: TextStyle(color: Color(0xFF757575), fontSize: 13, fontWeight: FontWeight.w500)),
              const SizedBox(height: 32),
              _logItem("Successful Login from 192.168.137.1", "Today, 10:45 AM", true),
              const SizedBox(height: 16),
              _logItem("Failed Login Attempt (Invalid Pass)", "Yesterday, 11:20 PM", false),
              const SizedBox(height: 40),
              ElevatedButton(
                onPressed: () => Navigator.pop(context),
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF2E7D32),
                  minimumSize: const Size(double.infinity, 56),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  elevation: 0,
                ),
                child: const Text("Export Logs (CSV)", style: TextStyle(color: Colors.white, fontWeight: FontWeight.w900)),
              ),
              const SizedBox(height: 12),
              TextButton(onPressed: () => Navigator.pop(context), child: const Text("CLOSE", style: TextStyle(color: Color(0xFF757575), fontWeight: FontWeight.w900))),
            ],
          ),
        ),
      ),
    );
  }

  void _showPermissionsDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(32)),
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  const Icon(Icons.person_outline_rounded, color: Color(0xFF2E7D32), size: 28),
                  const SizedBox(width: 12),
                  const Text("User Permissions", style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900, color: Color(0xFF2E7D32))),
                ],
              ),
              const SizedBox(height: 12),
              const Text("Define what different user roles can access.", style: TextStyle(color: Color(0xFF757575), fontSize: 13, fontWeight: FontWeight.w500)),
              const SizedBox(height: 32),
              _permissionRow("Manage Database", "Full access to resident and truck data", true),
              const Padding(padding: EdgeInsets.symmetric(horizontal: 12), child: Divider(height: 32, color: Color(0xFFF5F5F5))),
              _permissionRow("View Analytics", "Read-only access to system reports", true),
              const SizedBox(height: 40),
              ElevatedButton(
                onPressed: () => Navigator.pop(context),
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF2E7D32),
                  minimumSize: const Size(double.infinity, 56),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  elevation: 0,
                ),
                child: const Text("Apply Changes", style: TextStyle(color: Colors.white, fontWeight: FontWeight.w900)),
              ),
              const SizedBox(height: 12),
              TextButton(onPressed: () => Navigator.pop(context), child: const Text("CLOSE", style: TextStyle(color: Color(0xFF757575), fontWeight: FontWeight.w900))),
            ],
          ),
        ),
      ),
    );
  }

  // --- Helper Components ---

  Widget _buildSectionCard(IconData icon, String title, List<Widget> children) {
    return Container(
      margin: const EdgeInsets.fromLTRB(20, 0, 20, 24),
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(28),
        boxShadow: [BoxShadow(color: Colors.black.withAlpha(6), blurRadius: 15, offset: const Offset(0, 8))]
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 22, color: const Color(0xFF1A1A1A)),
              const SizedBox(width: 12),
              Text(title, style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 16, color: Color(0xFF1A1A1A))),
            ],
          ),
          const SizedBox(height: 24),
          ...children,
        ],
      ),
    );
  }

  Widget _buildProfileItem(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 20),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(label, style: const TextStyle(fontSize: 12, color: Color(0xFFBDBDBD), fontWeight: FontWeight.w700)),
        const SizedBox(height: 4),
        Text(value, style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 16, color: Color(0xFF1A1A1A))),
      ]),
    );
  }

  Widget _buildSwitchRow(String title, String subtitle, bool value, Function(bool) onChanged) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 20),
      child: Row(children: [
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title, style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 16, color: Color(0xFF1A1A1A))),
          Text(subtitle, style: const TextStyle(fontSize: 12, color: Color(0xFFBDBDBD), fontWeight: FontWeight.w600)),
        ])),
        Switch(value: value, onChanged: onChanged, activeColor: const Color(0xFF00BFA5)),
      ]),
    );
  }

  Widget _buildActionLink(String title, String subtitle, Color color, VoidCallback onTap) {
    return InkWell(
      onTap: onTap,
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title, style: TextStyle(fontWeight: FontWeight.w900, fontSize: 16, color: color)),
        Text(subtitle, style: TextStyle(fontSize: 12, color: color.withAlpha(150), fontWeight: FontWeight.w600)),
      ]),
    );
  }

  Widget _buildSecurityRow(String title, VoidCallback onTap) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 20, horizontal: 8),
        child: Row(children: [
          Expanded(child: Text(title, style: const TextStyle(fontSize: 15, color: Color(0xFF757575), fontWeight: FontWeight.w700))),
          const Icon(Icons.chevron_right_rounded, size: 20, color: Color(0xFFD1D1D1)),
        ]),
      ),
    );
  }

  Widget _dialogTextField(String hint) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 4),
      decoration: BoxDecoration(color: const Color(0xFFF8F9FA), borderRadius: BorderRadius.circular(16)),
      child: TextField(
        obscureText: true,
        decoration: InputDecoration(
          border: InputBorder.none,
          hintText: hint,
          hintStyle: const TextStyle(color: Color(0xFF757575), fontWeight: FontWeight.w500),
          suffixIcon: const Icon(Icons.visibility_off_outlined, color: Color(0xFF757575), size: 20),
        ),
      ),
    );
  }

  Widget _logItem(String msg, String time, bool isSuccess) {
    return Row(
      children: [
        Container(width: 8, height: 8, decoration: BoxDecoration(color: isSuccess ? Colors.green : Colors.red, shape: BoxShape.circle)),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(msg, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 14, color: Color(0xFF1A1A1A))),
              Text(time, style: const TextStyle(fontSize: 11, color: Colors.grey)),
            ],
          ),
        ),
      ],
    );
  }

  Widget _permissionRow(String title, String subtitle, bool value) {
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 15, color: Color(0xFF1A1A1A))),
              Text(subtitle, style: const TextStyle(fontSize: 12, color: Color(0xFF757575), fontWeight: FontWeight.w500)),
            ],
          ),
        ),
        Checkbox(value: value, onChanged: (v) {}, activeColor: const Color(0xFF2E7D32), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4))),
      ],
    );
  }

  void _showLogoutDialog(BuildContext context) {
    // Already synced with Residents Dashboard in AdminDashboard refactor, re-implementing here for screen completeness
    showDialog(
      context: context,
      builder: (context) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(32)),
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(padding: const EdgeInsets.all(16), decoration: const BoxDecoration(color: Color(0xFFFFF0F2), shape: BoxShape.circle), child: const Icon(Icons.logout_rounded, color: Color(0xFFFF1744), size: 32)),
              const SizedBox(height: 24),
              const Text("Secure Sign Out?", style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
              const SizedBox(height: 16),
              const Text("Are you sure you want to end your current session? You'll need to re-authenticate to access your dashboard.", textAlign: TextAlign.center, style: TextStyle(color: Color(0xFF757575), fontSize: 14, height: 1.5)),
              const SizedBox(height: 32),
              ElevatedButton(
                onPressed: () async {
                  await SessionManager.logout();
                  if (!mounted) return;
                  Navigator.pushReplacementNamed(context, '/');
                },
                style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF00BFA5), minimumSize: const Size(double.infinity, 56), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)), elevation: 0),
                child: const Text("Sign Out", style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w900)),
              ),
              const SizedBox(height: 12),
              TextButton(onPressed: () => Navigator.pop(context), child: const Text("Cancel", style: TextStyle(color: Color(0xFF1A1A1A), fontWeight: FontWeight.w900))),
            ],
          ),
        ),
      ),
    );
  }
}
