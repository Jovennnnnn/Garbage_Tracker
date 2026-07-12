import 'package:flutter/material.dart';
import 'package:firebase_database/firebase_database.dart';
import '../api/api_service.dart';
import '../utils/session_manager.dart';
import '../utils/app_theme.dart';
import 'analytics_screen.dart';
import 'track_trucks_screen.dart';
import 'complaints_screen.dart';
import 'admin_settings_screen.dart';

class AdminDashboard extends StatefulWidget {
  const AdminDashboard({super.key});

  @override
  State<AdminDashboard> createState() => _AdminDashboardState();
}

class _AdminDashboardState extends State<AdminDashboard> {
  final FirebaseDatabase _database = FirebaseDatabase.instance;
  final ApiService _apiService = ApiService();

  int _activeTrucks = 0;
  int _pendingComplaints = 0;
  int _inProgressComplaints = 0;
  int _residentsCount = 0;
  double _coveragePercent = 0.0;
  int _selectedIndex = 0;

  List<Map<dynamic, dynamic>> _fleetStatus = [];

  @override
  void initState() {
    super.initState();
    _setupListeners();
    _refreshAllStats();
  }

  void _refreshAllStats() {
    _fetchComplaints();
    _fetchUserCounts();
  }

  Future<void> _fetchUserCounts() async {
    try {
      final response = await _apiService.getUsers();
      if (response.data['success'] == true) {
        final List residents = response.data['residents'] ?? [];
        if (mounted) {
          setState(() {
            _residentsCount = residents.length;
          });
        }
      }
    } catch (e) {
      debugPrint("Error fetching user counts: $e");
    }
  }

  void _setupListeners() {
    _database.ref('truck_locations').onValue.listen((event) {
      if (event.snapshot.exists) {
        final Map data = event.snapshot.value as Map;
        final List<Map<dynamic, dynamic>> trucks = [];
        data.forEach((key, value) {
          trucks.add(Map<dynamic, dynamic>.from(value as Map));
        });

        if (mounted) {
          setState(() {
            _activeTrucks = trucks.where((t) => t['status'] == 'active').length;
            _fleetStatus = trucks;
          });
        }
      }
    });

    // Real-time Driver Routes Listener
    _database.ref('driver_routes').onValue.listen((event) {
      if (event.snapshot.exists) {
        final Map data = event.snapshot.value as Map;
        int total = 0;
        int completed = 0;
        data.forEach((key, value) {
          total++;
          if (value['route_status'] == 'COMPLETED') completed++;
        });
        if (mounted) {
          setState(() {
            _coveragePercent = total > 0 ? (completed / total) * 100 : 0.0;
          });
        }
      }
    });
  }

  Future<void> _fetchComplaints() async {
    try {
      final response = await _apiService.getComplaints();
      if (response.data['success'] == true) {
        final List complaints = response.data['data'] ?? [];
        
        // Robust normalization to match database exactly (ENUM: pending, in_progress, resolved)
        String normalize(dynamic s) {
          if (s == null) return 'PENDING';
          String str = s.toString().toLowerCase().trim();
          if (str == 'in_progress' || (str.contains('in') && str.contains('progress'))) return 'IN_PROGRESS';
          if (str == 'resolved' || str == 'completed') return 'RESOLVED';
          return 'PENDING';
        }
        
        if (mounted) {
          setState(() {
            _pendingComplaints = complaints
                .where((c) => normalize(c['status']) == 'PENDING')
                .length;
            _inProgressComplaints = complaints
                .where((c) => normalize(c['status']) == 'IN_PROGRESS')
                .length;
          });
        }
      }
    } catch (e) {
      debugPrint("Error fetching complaints: $e");
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      body: IndexedStack(
        index: _selectedIndex,
        children: [
          _buildMainDashboard(),
          TrackTrucksScreen(
            isEmbedded: true,
            onBack: () {
              _refreshAllStats();
              if (mounted) setState(() => _selectedIndex = 0);
            },
          ),
          AnalyticsScreen(
            isEmbedded: true,
            onBack: () {
              _refreshAllStats();
              if (mounted) setState(() => _selectedIndex = 0);
            },
          ),
          ComplaintsScreen(
            isEmbedded: true,
            onBack: () {
              _refreshAllStats();
              if (mounted) setState(() => _selectedIndex = 0);
            },
          ),
          AdminSettingsScreen(
            isEmbedded: true,
            onBack: () {
              _refreshAllStats();
              if (mounted) setState(() => _selectedIndex = 0);
            },
          ),
        ],
      ),
      bottomNavigationBar: _buildBottomNav(),
    );
  }

  Widget _buildMainDashboard() {
    return RefreshIndicator(
      onRefresh: () async => _refreshAllStats(),
      color: const Color(0xFF00BFA5),
      child: SingleChildScrollView(
        physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
        child: Column(
          children: [
            // Header
            Container(
              width: double.infinity,
              height: 240,
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
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        width: 44,
                        height: 44,
                        decoration: BoxDecoration(
                          color: Colors.white.withAlpha(150),
                          borderRadius: BorderRadius.circular(14),
                        ),
                        child: const Icon(Icons.shield_rounded, color: AppColors.tealText, size: 24),
                      ),
                      const SizedBox(width: 12),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                        decoration: BoxDecoration(
                          color: AppColors.tealText.withAlpha(40),
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: const Text(
                          "ADMIN",
                          style: TextStyle(
                            color: AppColors.tealText,
                            fontSize: 12,
                            fontWeight: FontWeight.w900,
                            letterSpacing: 0.5,
                          ),
                        ),
                      ),
                      const Spacer(),
                      _buildHeaderIconButton(
                        Icons.notifications_none_rounded,
                        badgeCount: 4,
                        onTap: () => _showNotificationsModal(context),
                      ),
                      const SizedBox(width: 12),
                      _buildHeaderIconButton(
                        Icons.logout_rounded,
                        onTap: () => _showLogoutDialog(context),
                      ),
                    ],
                  ),
                  const SizedBox(height: 32),
                  const Text(
                    "Control Center",
                    style: TextStyle(
                      color: AppColors.tealText,
                      fontSize: 32,
                      fontWeight: FontWeight.w900,
                      letterSpacing: -0.8,
                    ),
                  ),
                  const Text(
                    "Barangay Balintawak, Lipa City",
                    style: TextStyle(
                      color: AppColors.textGray,
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),

            // Stat Cards
            Transform.translate(
              offset: const Offset(0, -32),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Column(
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: _buildStatCard(
                            "Active Trucks",
                            "$_activeTrucks",
                            Icons.local_shipping_outlined,
                            const Color(0xFF2E7D32),
                            const Color(0xFFE8F5E9),
                            showRefresh: true,
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: _buildStatCard(
                            "Pending Issues",
                            "$_pendingComplaints",
                            Icons.chat_bubble_outline_rounded,
                            const Color(0xFFD32F2F),
                            const Color(0xFFFFF1F2),
                            badge: "1",
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        Expanded(
                          child: _buildStatCard(
                            "In Progress",
                            "$_inProgressComplaints",
                            Icons.chat_bubble_outline_rounded,
                            const Color(0xFF1976D2),
                            const Color(0xFFE3F2FD),
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: _buildStatCard(
                            "Coverage",
                            "${_coveragePercent.toInt()}%",
                            Icons.computer_rounded,
                            const Color(0xFFFFA000),
                            const Color(0xFFFFF8E1),
                          ),
                        ),
                      ],
                    ),
                    Row(
                      children: [
                        Expanded(
                          child: _buildStatCard(
                            "Residents",
                            "$_residentsCount",
                            Icons.person_outline_rounded,
                            const Color(0xFF9C27B0),
                            const Color(0xFFF3E5F5),
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: Container(), // Spacer to keep layout balanced
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),

            // Real-time Tracking
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
              child: Row(
                children: const [
                  Icon(Icons.location_on_rounded, color: Color(0xFF1A1A1A), size: 24),
                  SizedBox(width: 8),
                  Text(
                    "Real-time Tracking",
                    style: TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.w900,
                      color: Color(0xFF1A1A1A),
                    ),
                  ),
                ],
              ),
            ),
            Container(
              margin: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(32),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withAlpha(12),
                    blurRadius: 20,
                    offset: const Offset(0, 10),
                  ),
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
                        Row(
                          children: const [
                            SizedBox(
                              width: 10,
                              height: 10,
                              child: DecoratedBox(
                                decoration: BoxDecoration(
                                  color: Color(0xFF00C853),
                                  shape: BoxShape.circle,
                                ),
                              ),
                            ),
                            SizedBox(width: 10),
                            Text(
                              "Live Monitoring",
                              style: TextStyle(
                                fontWeight: FontWeight.w800,
                                color: Color(0xFF1A1A1A),
                                fontSize: 16,
                              ),
                            ),
                          ],
                        ),
                        GestureDetector(
                          onTap: () => setState(() => _selectedIndex = 1),
                          child: const Text(
                            "Full Map",
                            style: TextStyle(
                              color: Color(0xFF00BFA5),
                              fontWeight: FontWeight.w800,
                              fontSize: 14,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  Container(
                    height: 180,
                    width: double.infinity,
                    margin: const EdgeInsets.symmetric(horizontal: 16),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(20),
                      color: const Color(0xFFF5F5F5),
                    ),
                    child: const Center(
                      child: Icon(Icons.map_outlined, color: Colors.black12, size: 48),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(20),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        _buildLegendItem(const Color(0xFF00C853), "Active"),
                        const SizedBox(width: 20),
                        _buildLegendItem(const Color(0xFFFFAB00), "Idle"),
                        const SizedBox(width: 20),
                        _buildLegendItem(const Color(0xFF9E9E9E), "Offline"),
                      ],
                    ),
                  ),
                ],
              ),
            ),

            // Fleet Status
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 24, 24, 12),
              child: Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: const Color(0xFFE3F2FD),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Icon(
                      Icons.local_shipping_rounded,
                      color: Color(0xFF1976D2),
                      size: 20,
                    ),
                  ),
                  const SizedBox(width: 12),
                  const Text(
                    "Fleet Status",
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w900,
                      color: Color(0xFF1A1A1A),
                    ),
                  ),
                ],
              ),
            ),
            Container(
              margin: const EdgeInsets.symmetric(horizontal: 20),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(32),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withAlpha(10),
                    blurRadius: 18,
                    offset: const Offset(0, 8),
                  ),
                ],
                border: Border.all(color: const Color(0xFFF5F5F5)),
              ),
              child: Column(
                children: _fleetStatus.isEmpty
                    ? [
                  const Padding(
                    padding: EdgeInsets.all(20),
                    child: Text("No trucks active", style: TextStyle(color: Colors.grey)),
                  )
                ]
                    : _fleetStatus.take(2).map((truck) => _buildFleetItem(truck)).toList(),
              ),
            ),

            // Admin Actions
            const Padding(
              padding: EdgeInsets.fromLTRB(24, 32, 24, 12),
              child: Text(
                "Admin Actions",
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w900,
                  color: Color(0xFF1A1A1A),
                ),
              ),
            ),
            Container(
              margin: const EdgeInsets.symmetric(horizontal: 20),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(32),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withAlpha(10),
                    blurRadius: 18,
                    offset: const Offset(0, 8),
                  ),
                ],
                border: Border.all(color: const Color(0xFFF5F5F5)),
              ),
              child: Column(
                children: [
                  _buildActionRow(
                    "Analytics & Reports",
                    "View system insights",
                    Icons.image_outlined,
                    const Color(0xFF03A9F4),
                    const Color(0xFFE1F5FE),
                    onTap: () => setState(() => _selectedIndex = 2),
                  ),
                  const Padding(
                    padding: EdgeInsets.symmetric(horizontal: 20),
                    child: Divider(height: 1, color: Color(0xFFF5F5F5)),
                  ),
                  _buildActionRow(
                    "Manage Users",
                    "Edit user data",
                    Icons.person_outline_rounded,
                    const Color(0xFF9C27B0),
                    const Color(0xFFF3E5F5),
                    onTap: () => Navigator.pushNamed(context, '/user_management'),
                  ),
                  const Padding(
                    padding: EdgeInsets.symmetric(horizontal: 20),
                    child: Divider(height: 1, color: Color(0xFFF5F5F5)),
                  ),
                  _buildActionRow(
                    "Complaints & Issues",
                    "Respond to resident and driver reports",
                    Icons.chat_bubble_outline_rounded,
                    const Color(0xFFFFA000),
                    const Color(0xFFFFF8E1),
                    badge: _pendingComplaints > 0 ? "$_pendingComplaints" : null,
                    onTap: () => setState(() => _selectedIndex = 3),
                  ),
                ],
              ),
            ),

            // Today's Summary
            Container(
              margin: const EdgeInsets.fromLTRB(20, 32, 20, 40),
              padding: const EdgeInsets.all(28),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(32),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withAlpha(8),
                    blurRadius: 20,
                    offset: const Offset(0, 10),
                  ),
                ],
              ),
              child: Column(
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(10),
                        decoration: BoxDecoration(
                          color: const Color(0xFFF5F5F5),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: const Icon(
                          Icons.access_time_rounded,
                          color: Color(0xFF616161),
                          size: 22,
                        ),
                      ),
                      const SizedBox(width: 16),
                      const Text(
                        "Today's Summary",
                        style: TextStyle(
                          fontWeight: FontWeight.w900,
                          color: Color(0xFF1A1A1A),
                          fontSize: 18,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 32),
                  _buildSummaryProgress("Routes Completed", "0 / 12", 0.0),
                  const SizedBox(height: 24),
                  _buildSummaryTextRow("Distance Covered", "15.1 km"),
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 16),
                    child: Divider(height: 1, color: Color(0xFFF5F5F5)),
                  ),
                  _buildSummaryTextRow("Complaints Resolved", "0", isSuccess: true),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeaderIconButton(IconData icon, {int? badgeCount, VoidCallback? onTap}) {
    return GestureDetector(
      onTap: onTap,
      child: Stack(
        children: [
          Container(
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
          if (badgeCount != null)
            Positioned(
              right: 6,
              top: 6,
              child: Container(
                padding: const EdgeInsets.all(4),
                decoration: const BoxDecoration(
                  color: Color(0xFFFF4081),
                  shape: BoxShape.circle,
                ),
                child: Text(
                  "$badgeCount",
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 8,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildStatCard(
      String title,
      String value,
      IconData icon,
      Color accentColor,
      Color bgColor, {
        bool showRefresh = false,
        String? badge,
      }) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(28),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withAlpha(8),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Icon(icon, color: accentColor, size: 22),
              ),
              if (showRefresh)
                const Icon(Icons.refresh_rounded, color: Colors.black26, size: 20)
              else if (badge != null)
                Container(
                  padding: const EdgeInsets.all(6),
                  decoration: const BoxDecoration(
                    color: Color(0xFFFF4081),
                    shape: BoxShape.circle,
                  ),
                  child: Text(
                    badge,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 10,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 24),
          Text(
            title,
            style: const TextStyle(
              fontSize: 12,
              color: Color(0xFF757575),
              fontWeight: FontWeight.w700,
            ),
          ),
          Text(
            value,
            style: const TextStyle(
              fontSize: 24,
              fontWeight: FontWeight.w900,
              color: Color(0xFF1A1A1A),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLegendItem(Color color, String label) {
    return Row(
      children: [
        Container(
          width: 8,
          height: 8,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: 8),
        Text(
          label,
          style: const TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w700,
            color: Color(0xFF757575),
          ),
        ),
      ],
    );
  }

  Widget _buildFleetItem(Map<dynamic, dynamic> truck) {
    final String id = truck['truck_id'] ?? "GT-001";
    final String location = truck['location'] ?? "Base / Depot";
    final String speed = "${truck['speed'] ?? 0} km/h";
    final String status = (truck['status'] ?? "Idle").toString().toUpperCase();

    final Color statusColor = switch (status) {
      'ACTIVE' => const Color(0xFF4CAF50),
      'FULL' => const Color(0xFFFF1744),
      _ => const Color(0xFFFFAB00),
    };

    return Padding(
      padding: const EdgeInsets.all(20),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: statusColor.withAlpha(30),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(Icons.local_shipping_rounded, color: statusColor, size: 24),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      id,
                      style: const TextStyle(
                        fontWeight: FontWeight.w900,
                        fontSize: 16,
                        color: Color(0xFF1A1A1A),
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: statusColor.withAlpha(30),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        status,
                        style: TextStyle(
                          color: statusColor,
                          fontSize: 10,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    const Icon(Icons.location_on_rounded, size: 14, color: Color(0xFF1976D2)),
                    const SizedBox(width: 4),
                    Text(
                      location,
                      style: const TextStyle(fontSize: 12, color: Color(0xFF757575)),
                    ),
                    const SizedBox(width: 16),
                    const Icon(Icons.speed_rounded, size: 14, color: Color(0xFF9C27B0)),
                    const SizedBox(width: 4),
                    Text(
                      speed,
                      style: const TextStyle(fontSize: 12, color: Color(0xFF757575)),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActionRow(
      String title,
      String subtitle,
      IconData icon,
      Color iconColor,
      Color bgColor, {
        String? badge,
        VoidCallback? onTap,
      }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(28),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          children: [
            Container(
              width: 52,
              height: 52,
              decoration: BoxDecoration(
                color: bgColor,
                borderRadius: BorderRadius.circular(16),
              ),
              child: Icon(icon, color: iconColor, size: 26),
            ),
            const SizedBox(width: 20),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: const TextStyle(
                      fontWeight: FontWeight.w800,
                      fontSize: 16,
                      color: Color(0xFF1A1A1A),
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    subtitle,
                    style: const TextStyle(
                      fontSize: 12,
                      color: Color(0xFF757575),
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ],
              ),
            ),
            if (badge != null)
              Container(
                width: 24,
                height: 24,
                margin: const EdgeInsets.only(right: 12),
                decoration: const BoxDecoration(
                  color: Color(0xFFFF7043),
                  shape: BoxShape.circle,
                ),
                alignment: Alignment.center,
                child: Text(
                  badge,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 10,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ),
            const Icon(Icons.chevron_right_rounded, color: Color(0xFFD1D1D1)),
          ],
        ),
      ),
    );
  }

  Widget _buildSummaryProgress(String label, String value, double progress) {
    return Column(
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              label,
              style: const TextStyle(
                color: Color(0xFF757575),
                fontSize: 14,
                fontWeight: FontWeight.w600,
              ),
            ),
            Text(
              value,
              style: const TextStyle(
                color: Color(0xFF1A1A1A),
                fontWeight: FontWeight.w900,
                fontSize: 14,
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        ClipRRect(
          borderRadius: BorderRadius.circular(10),
          child: LinearProgressIndicator(
            value: progress,
            backgroundColor: const Color(0xFFF5F5F5),
            valueColor: const AlwaysStoppedAnimation<Color>(Color(0xFF1E88E5)),
            minHeight: 6,
          ),
        ),
      ],
    );
  }

  Widget _buildSummaryTextRow(String label, String value, {bool isSuccess = false}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: const TextStyle(
            color: Color(0xFF757575),
            fontSize: 14,
            fontWeight: FontWeight.w600,
          ),
        ),
        Row(
          children: [
            Text(
              value,
              style: const TextStyle(
                color: Color(0xFF1A1A1A),
                fontWeight: FontWeight.w900,
                fontSize: 14,
              ),
            ),
            if (isSuccess) ...[
              const SizedBox(width: 8),
              const Icon(Icons.check_circle, color: Color(0xFF4CAF50), size: 18),
            ],
          ],
        ),
      ],
    );
  }

  Widget _buildBottomNav() {
    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        boxShadow: [
          BoxShadow(color: Colors.black12, blurRadius: 25, offset: Offset(0, -5)),
        ],
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
          _buildNavItem(Icons.home_rounded, 'Monitor', 0),
          _buildNavItem(Icons.location_on_rounded, 'Track', 1),
          _buildNavItem(Icons.analytics_rounded, 'Analytics', 2),
          _buildNavItem(Icons.chat_bubble_rounded, 'Issues', 3),
          _buildNavItem(Icons.settings_suggest_rounded, 'Settings', 4),
        ],
        onTap: (index) {
          if (index == 0) _refreshAllStats();
          setState(() => _selectedIndex = index);
        },
      ),
    );
  }

  BottomNavigationBarItem _buildNavItem(IconData icon, String label, int index) {
    final bool isSelected = _selectedIndex == index;
    return BottomNavigationBarItem(
      icon: AnimatedContainer(
        duration: const Duration(milliseconds: 300),
        margin: const EdgeInsets.only(bottom: 4),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
        decoration: isSelected
            ? BoxDecoration(
          color: const Color(0xFFE0F2F1),
          borderRadius: BorderRadius.circular(24),
        )
            : null,
        child: Icon(icon, size: 24),
      ),
      label: label,
    );
  }

  void _showNotificationsModal(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(32)),
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  const Icon(Icons.notifications_active_outlined, color: Color(0xFF00BFA5), size: 28),
                  const SizedBox(width: 12),
                  const Expanded(
                    child: Text(
                      "System Notifications",
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w900,
                        color: Color(0xFF1A1A1A),
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  TextButton(
                    onPressed: () {},
                    style: TextButton.styleFrom(padding: EdgeInsets.zero, minimumSize: const Size(50, 30)),
                    child: const Text(
                      "Clear All",
                      style: TextStyle(
                        color: Color(0xFF00BFA5),
                        fontWeight: FontWeight.w900,
                        fontSize: 13,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              const Align(
                alignment: Alignment.centerLeft,
                child: Text(
                  "Recently received system notifications",
                  style: TextStyle(color: Color(0xFF757575), fontSize: 13),
                ),
              ),
              const SizedBox(height: 24),
              Flexible(
                child: SingleChildScrollView(
                  child: Column(
                    children: [
                      _buildNotificationItem(
                        "Truck Full: GT-001",
                        "Truck GT-001 has been automatically marked as full",
                        "57 minutes ago",
                        Icons.local_shipping_rounded,
                        const Color(0xFFFFFDE7),
                        const Color(0xFFFBC02D),
                      ),
                      const SizedBox(height: 12),
                      _buildNotificationItem(
                        "New Registration Request",
                        "Jubenn has requested to join as a Resident in Sentro",
                        "1 hour ago",
                        Icons.add_circle_outline_rounded,
                        const Color(0xFFE8F5E9),
                        const Color(0xFF4CAF50),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 32),
              ElevatedButton(
                onPressed: () => Navigator.pop(context),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.white,
                  foregroundColor: const Color(0xFF1A1A1A),
                  minimumSize: const Size(double.infinity, 56),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                    side: const BorderSide(color: Color(0xFFF5F5F5)),
                  ),
                  elevation: 0,
                ),
                child: const Text(
                  "CLOSE",
                  style: TextStyle(fontWeight: FontWeight.w900, letterSpacing: 1.2),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildNotificationItem(
      String title,
      String message,
      String time,
      IconData icon,
      Color bgColor,
      Color iconColor,
      ) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: const Color(0xFFF5F5F5)),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: bgColor,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Icon(icon, color: iconColor, size: 24),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontWeight: FontWeight.w900,
                    fontSize: 14,
                    color: Color(0xFF1A1A1A),
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  message,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 12, color: Color(0xFF757575)),
                ),
                const SizedBox(height: 4),
                Text(
                  time,
                  style: const TextStyle(fontSize: 11, color: Color(0xFFBDBDBD)),
                ),
              ],
            ),
          ),
          Container(
            width: 8,
            height: 8,
            decoration: const BoxDecoration(color: Color(0xFFFF4081), shape: BoxShape.circle),
          ),
        ],
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
              Container(
                padding: const EdgeInsets.all(16),
                decoration: const BoxDecoration(color: Color(0xFFFFF0F2), shape: BoxShape.circle),
                child: const Icon(Icons.logout_rounded, color: Color(0xFFFF1744), size: 32),
              ),
              const SizedBox(height: 24),
              const Text(
                "Secure Sign Out?",
                style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A)),
              ),
              const SizedBox(height: 16),
              const Text(
                "Are you sure you want to end your current session? You'll need to re-authenticate to access your dashboard.",
                textAlign: TextAlign.center,
                style: TextStyle(color: Color(0xFF757575), fontSize: 14, height: 1.5),
              ),
              const SizedBox(height: 32),
              ElevatedButton(
                onPressed: () async {
                  await SessionManager.logout();
                  if (!mounted) return;
                  Navigator.pushReplacementNamed(context, '/');
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF00BFA5),
                  minimumSize: const Size(double.infinity, 56),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  elevation: 0,
                ),
                child: const Text(
                  "Sign Out",
                  style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w900),
                ),
              ),
              const SizedBox(height: 12),
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text(
                  "Cancel",
                  style: TextStyle(color: Color(0xFF1A1A1A), fontWeight: FontWeight.w900),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// Separated BottomNav widget
