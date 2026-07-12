import 'dart:async';
import 'package:flutter/material.dart';
import 'package:firebase_database/firebase_database.dart';
import '../utils/session_manager.dart';
import '../models/user.dart';
import '../utils/app_theme.dart';
import 'resident_track_truck_screen.dart';
import 'resident_complaints_screen.dart';
import 'resident_settings_screen.dart';

class ResidentDashboard extends StatefulWidget {
  const ResidentDashboard({super.key});

  @override
  State<ResidentDashboard> createState() => _ResidentDashboardState();
}

class _ResidentDashboardState extends State<ResidentDashboard> {
  final FirebaseDatabase _database = FirebaseDatabase.instance;
  UserData? _user;
  int _activeTrucks = 0;
  int _selectedIndex = 0;
  int _userRating = 0;

  @override
  void initState() {
    super.initState();
    _loadUser();
    _setupListeners();
  }

  void _loadUser() async {
    _user = await SessionManager.getUser();
    if (mounted) setState(() {});
  }

  void _setupListeners() {
    _database.ref('truck_locations').onValue.listen((event) {
      if (event.snapshot.exists) {
        final Map data = event.snapshot.value as Map;
        if (mounted) {
          setState(() {
            _activeTrucks = data.values.where((t) => t['status'] == 'active').length;
          });
        }
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      body: IndexedStack(
        index: _selectedIndex,
        children: [
          _buildHomeTab(),
          ResidentTrackTruckScreen(isEmbedded: true, onBack: () => setState(() => _selectedIndex = 0)),
          ResidentComplaintsScreen(isEmbedded: true, onBack: () => setState(() => _selectedIndex = 0)),
          ResidentSettingsScreen(isEmbedded: true, onBack: () => setState(() => _selectedIndex = 0)),
        ],
      ),
      bottomNavigationBar: _buildBottomNav(),
    );
  }

  Widget _buildHomeTab() {
    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      child: Column(
        children: [
          // 🏠 ORGANIZED HEADER
          Container(
            width: double.infinity,
            height: 220,
            decoration: const BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [
                  Color(0xFFE0F7FA),
                  Color(0xFFB2DFDB),
                  Color(0xFF80CBC4),
                ],
              ),
              borderRadius: BorderRadius.vertical(bottom: Radius.circular(44)),
              boxShadow: [
                BoxShadow(
                  color: Colors.black12,
                  blurRadius: 20,
                  offset: Offset(0, 5),
                ),
              ],
            ),
            padding: const EdgeInsets.fromLTRB(28, 64, 28, 28),
            child: Column(
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text("Good morning 👋", style: TextStyle(color: AppColors.textGray, fontSize: 14, fontWeight: FontWeight.w600)),
                        const SizedBox(height: 2),
                        Text(
                          _user?.name ?? "Jubennn",
                          style: const TextStyle(color: AppColors.tealText, fontSize: 32, fontWeight: FontWeight.w900, letterSpacing: -0.8),
                        ),
                        const SizedBox(height: 4),
                        Row(
                          children: [
                            const Icon(Icons.location_on_rounded, color: AppColors.tealText, size: 14),
                            const SizedBox(width: 4),
                            Text(_user?.purok ?? "Sentro", style: const TextStyle(color: AppColors.textGray, fontSize: 13, fontWeight: FontWeight.bold)),
                          ],
                        ),
                      ],
                    ),
                    Row(
                      children: [
                        _buildHeaderIconButton(Icons.notifications_none_rounded, onTap: () => _showNotificationsModal(context)),
                        const SizedBox(width: 12),
                        _buildHeaderIconButton(Icons.power_settings_new_rounded, onTap: () => _showLogoutDialog(context)),
                      ],
                    ),
                  ],
                ),
              ],
            ),
          ),

          // 📊 UNIFORM STAT CARDS
          Transform.translate(
            offset: const Offset(0, -32),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Row(
                children: [
                  Expanded(child: _buildStatCard("Active Trucks", "$_activeTrucks", Icons.local_shipping_outlined, const Color(0xFF00BFA5), const Color(0xFFE8F5E9), isLive: true)),
                  const SizedBox(width: 12),
                  Expanded(child: _buildStatCard("Arrival Status", "Nearby", Icons.access_time_rounded, const Color(0xFFFFA000), const Color(0xFFFFF8E1))),
                ],
              ),
            ),
          ),

          // 🗺️ CLEAN TRACKING CARD
          _buildSectionTitle("Real-time Overview"),
          Container(
            margin: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(32),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withAlpha(12),
                  blurRadius: 20,
                  offset: const Offset(0, 10)
                )
              ],
              border: Border.all(color: const Color(0xFFF5F5F5)),
            ),
            child: Column(
              children: [
                Padding(
                  padding: const EdgeInsets.all(20),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text("Live Tracking", style: TextStyle(fontWeight: FontWeight.w800, color: Color(0xFF1A1A1A), fontSize: 17)),
                          Text("Direct fleet GPS updates", style: TextStyle(fontSize: 12, color: Color(0xFF757575))),
                        ],
                      ),
                      TextButton(
                        onPressed: () => setState(() => _selectedIndex = 1),
                        child: const Text("View Full →", style: TextStyle(color: Color(0xFF00BFA5), fontWeight: FontWeight.w800, fontSize: 14)),
                      ),
                    ],
                  ),
                ),
                Container(
                  height: 160,
                  width: double.infinity,
                  margin: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                  decoration: BoxDecoration(borderRadius: BorderRadius.circular(20), color: const Color(0xFFF5F5F5)),
                  child: const Center(child: Icon(Icons.map_outlined, color: Colors.black12, size: 40)),
                ),
              ],
            ),
          ),

          // ⚡ ORGANIZED QUICK ACTIONS
          _buildSectionTitle("Quick Actions"),
          _buildActionCard("Track Trucks", "Check location on map", Icons.map_outlined, const Color(0xFF1E88E5), const Color(0xFFE3F2FD), () => setState(() => _selectedIndex = 1)),
          _buildActionCard("Report Issue", "File a system complaint", Icons.error_outline_rounded, const Color(0xFFFF1744), const Color(0xFFFFF0F2), () => Navigator.pushNamed(context, '/file_complaint')),
          _buildActionCard("Service Quality", "Rate your experience", Icons.star_outline_rounded, const Color(0xFF9C27B0), const Color(0xFFF3E5F5), () => _showRateServiceModal(context)),

          // 📅 SCHEDULE CARD
          _buildSectionTitle("Regional Schedule"),
          Container(
            margin: const EdgeInsets.fromLTRB(20, 8, 20, 40),
            padding: const EdgeInsets.all(28),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(32),
              boxShadow: [BoxShadow(color: Colors.black.withAlpha(8), blurRadius: 20, offset: const Offset(0, 10))],
            ),
            child: Column(
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(color: const Color(0xFFE8EAF6), borderRadius: BorderRadius.circular(16)),
                      child: const Icon(Icons.calendar_today_rounded, color: Color(0xFF3F51B5), size: 22),
                    ),
                    const SizedBox(width: 16),
                    const Text("Collection Schedule", style: TextStyle(fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A), fontSize: 18)),
                  ],
                ),
                const SizedBox(height: 32),
                _buildScheduleRow("Service Frequency", "Daily", isBadge: true),
                const Padding(padding: EdgeInsets.symmetric(vertical: 12), child: Divider(height: 1, color: Color(0xFFF5F5F5))),
                _buildScheduleRow("Active Window", "8:00 AM - 12:00 PM"),
                const Padding(padding: EdgeInsets.symmetric(vertical: 12), child: Divider(height: 1, color: Color(0xFFF5F5F5))),
                _buildScheduleRow("Designated Area", _user?.purok ?? "Sentro", isPurok: true),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // --- REFINED UI COMPONENTS ---

  Widget _buildSectionTitle(String title) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 24, 24, 8),
      child: Align(
        alignment: Alignment.centerLeft,
        child: Text(title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
      ),
    );
  }

  Widget _buildHeaderIconButton(IconData icon, {VoidCallback? onTap}) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 48,
        height: 48,
        decoration: BoxDecoration(
          color: Colors.white.withAlpha(180),
          borderRadius: BorderRadius.circular(14),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withAlpha(5),
              blurRadius: 10,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Icon(icon, color: AppColors.tealText, size: 24),
      ),
    );
  }

  Widget _buildStatCard(String title, String value, IconData icon, Color accentColor, Color bgColor, {bool isLive = false}) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(28),
        boxShadow: [BoxShadow(color: Colors.black.withAlpha(8), blurRadius: 12, offset: const Offset(0, 4))],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(14)),
                child: Icon(icon, color: accentColor, size: 22),
              ),
              if (isLive)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(color: const Color(0xFF00BFA5), borderRadius: BorderRadius.circular(12)),
                  child: const Text("LIVE", style: TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.w900)),
                ),
            ],
          ),
          const SizedBox(height: 24),
          Text(title, style: const TextStyle(fontSize: 12, color: Color(0xFF757575), fontWeight: FontWeight.w700)),
          Text(value, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
        ],
      ),
    );
  }

  Widget _buildActionCard(String title, String subtitle, IconData icon, Color iconColor, Color bgColor, VoidCallback onTap) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 20, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(24),
        boxShadow: [BoxShadow(color: Colors.black.withAlpha(6), blurRadius: 10, offset: const Offset(0, 4))],
      ),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(24),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Row(
            children: [
              Container(
                width: 52,
                height: 52,
                decoration: BoxDecoration(color: bgColor, borderRadius: BorderRadius.circular(16)),
                child: Icon(icon, color: iconColor, size: 26),
              ),
              const SizedBox(width: 20),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16, color: Color(0xFF1A1A1A))),
                    const SizedBox(height: 4),
                    Text(subtitle, style: const TextStyle(fontSize: 12, color: Color(0xFF757575), fontWeight: FontWeight.w500)),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right_rounded, color: Color(0xFFD1D1D1)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildScheduleRow(String label, String value, {bool isBadge = false, bool isPurok = false}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: const TextStyle(color: Color(0xFF757575), fontSize: 14, fontWeight: FontWeight.w600)),
        if (isBadge)
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
            decoration: BoxDecoration(color: const Color(0xFFE8F5E9), borderRadius: BorderRadius.circular(20)),
            child: Text(value, style: const TextStyle(color: Color(0xFF2E7D32), fontSize: 13, fontWeight: FontWeight.w900)),
          )
        else if (isPurok)
          Row(
            children: [
              const Icon(Icons.location_searching_rounded, color: Color(0xFF3F51B5), size: 16),
              const SizedBox(width: 8),
              Text(value, style: const TextStyle(color: Color(0xFF3F51B5), fontWeight: FontWeight.w900, fontSize: 14)),
            ],
          )
        else
          Text(value, style: const TextStyle(color: Color(0xFF1A1A1A), fontWeight: FontWeight.w900, fontSize: 14)),
      ],
    );
  }

  Widget _buildBottomNav() {
    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        boxShadow: [BoxShadow(color: Colors.black12, blurRadius: 25, offset: Offset(0, -5))],
      ),
      child: BottomNavigationBar(
        currentIndex: _selectedIndex,
        type: BottomNavigationBarType.fixed,
        backgroundColor: Colors.white,
        selectedItemColor: const Color(0xFF00BFA5),
        unselectedItemColor: const Color(0xFF9E9E9E),
        selectedLabelStyle: const TextStyle(fontWeight: FontWeight.w900, fontSize: 12),
        unselectedLabelStyle: const TextStyle(fontWeight: FontWeight.w500, fontSize: 11),
        elevation: 0,
        items: [
          _buildNavItem(Icons.home_rounded, 'Home', 0),
          _buildNavItem(Icons.location_on_rounded, 'Track', 1),
          _buildNavItem(Icons.chat_bubble_rounded, 'Issues', 2),
          _buildNavItem(Icons.settings_suggest_rounded, 'Config', 3),
        ],
        onTap: (index) => setState(() => _selectedIndex = index),
      ),
    );
  }

  BottomNavigationBarItem _buildNavItem(IconData icon, String label, int index) {
    bool isSelected = _selectedIndex == index;
    return BottomNavigationBarItem(
      icon: AnimatedContainer(
        duration: const Duration(milliseconds: 300),
        margin: const EdgeInsets.only(bottom: 4),
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 10),
        decoration: isSelected ? BoxDecoration(color: const Color(0xFFE0F2F1), borderRadius: BorderRadius.circular(24)) : null,
        child: Icon(icon, size: 24),
      ),
      label: label,
    );
  }

  // --- REFINED MODALS ---

  void _showNotificationsModal(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(32)),
        child: Padding(
          padding: const EdgeInsets.all(28),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.notifications_active_outlined, color: Color(0xFF00BFA5), size: 28),
                      SizedBox(width: 12),
                      Text("Notifications", style: TextStyle(fontSize: 22, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
                    ],
                  ),
                  TextButton(onPressed: () {}, child: const Text("Clear", style: TextStyle(color: Color(0xFF00BFA5), fontWeight: FontWeight.w900))),
                ],
              ),
              const SizedBox(height: 48),
              Container(
                width: 110,
                height: 110,
                decoration: const BoxDecoration(color: Color(0xFFF8F9FA), shape: BoxShape.circle),
                child: const Icon(Icons.done_all_rounded, size: 48, color: Color(0xFFD1D1D1)),
              ),
              const SizedBox(height: 24),
              const Text("You're all caught up!", style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: Color(0xFF1A1A1A))),
              const SizedBox(height: 8),
              const Text("No new system alerts at this time.", style: TextStyle(color: Color(0xFF757575), fontSize: 13)),
              const SizedBox(height: 48),
              ElevatedButton(
                onPressed: () => Navigator.pop(context),
                style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFFF5F5F5), foregroundColor: const Color(0xFF1A1A1A), minimumSize: const Size(double.infinity, 56), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)), elevation: 0),
                child: const Text("CLOSE", style: TextStyle(fontWeight: FontWeight.w900, letterSpacing: 1.2)),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showRateServiceModal(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setModalState) {
          return Dialog(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(32)),
            child: Padding(
              padding: const EdgeInsets.all(28),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text("Rate our Service", style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
                  const SizedBox(height: 12),
                  const Text("Your feedback helps us optimize the collection frequency in your area.", style: TextStyle(fontSize: 14, color: Color(0xFF757575), height: 1.5)),
                  const SizedBox(height: 40),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: List.generate(5, (index) {
                      return IconButton(
                        icon: Icon(
                          index < _userRating ? Icons.star_rounded : Icons.star_outline_rounded,
                          color: index < _userRating ? const Color(0xFFFFC107) : Colors.grey[300],
                          size: 44,
                        ),
                        onPressed: () => setModalState(() => _userRating = index + 1),
                      );
                    }),
                  ),
                  const SizedBox(height: 32),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 18),
                    decoration: BoxDecoration(color: const Color(0xFFF8F9FA), borderRadius: BorderRadius.circular(20), border: Border.all(color: const Color(0xFFF0F0F0))),
                    child: const TextField(
                      maxLines: 4,
                      decoration: InputDecoration(
                        hintText: "Tell us more... (Optional)",
                        hintStyle: TextStyle(color: Color(0xFFBDBDBD), fontSize: 14),
                        border: InputBorder.none,
                        contentPadding: EdgeInsets.symmetric(vertical: 18),
                      ),
                    ),
                  ),
                  const SizedBox(height: 32),
                  Row(
                    children: [
                      Expanded(child: TextButton(onPressed: () => Navigator.pop(context), child: const Text("CANCEL", style: TextStyle(color: Color(0xFF757575), fontWeight: FontWeight.w900)))),
                      const SizedBox(width: 16),
                      Expanded(
                        child: ElevatedButton(
                          onPressed: () => Navigator.pop(context),
                          style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF00BFA5), minimumSize: const Size(0, 56), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16))),
                          child: const Text("SUBMIT", style: TextStyle(color: Colors.white, fontWeight: FontWeight.w900)),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  void _showLogoutDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(32)),
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(padding: const EdgeInsets.all(16), decoration: BoxDecoration(color: const Color(0xFFFFF0F2), shape: BoxShape.circle), child: const Icon(Icons.logout_rounded, color: Color(0xFFFF1744), size: 32)),
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
