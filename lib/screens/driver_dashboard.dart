import 'dart:async';
import 'package:flutter/material.dart';
import 'package:firebase_database/firebase_database.dart';
import 'package:intl/intl.dart';
import 'package:mapbox_maps_flutter/mapbox_maps_flutter.dart' hide Size;
import '../utils/session_manager.dart';
import '../models/user.dart';
import '../utils/app_theme.dart';
import 'driver_settings_screen.dart';
import 'driver_track_truck_screen.dart';

class DriverDashboard extends StatefulWidget {
  const DriverDashboard({super.key});

  @override
  State<DriverDashboard> createState() => _DriverDashboardState();
}

class _DriverDashboardState extends State<DriverDashboard> with SingleTickerProviderStateMixin {
  final FirebaseDatabase _database = FirebaseDatabase.instance;
  UserData? _user;
  String _status = "OFFLINE";
  String _startTime = "--:--";
  String _estimatedEnd = "2:34 am";
  double _distance = 0.0;
  int _visitedCount = 0;
  final int _totalPuroks = 12;
  int _selectedIndex = 0;
  List<Map<dynamic, dynamic>> _puroks = [];
  bool _isLoadingPuroks = true;

  late AnimationController _headerAnimController;

  @override
  void initState() {
    super.initState();
    _loadUser();
    _headerAnimController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 12),
    )..repeat(reverse: true);
  }

  void _loadUser() async {
    _user = await SessionManager.getUser();
    if (_user != null) {
      if (mounted) setState(() {});
      _setupListeners();
      _initializeStatus();
    }
  }

  void _initializeStatus() async {
    final truckId = _user?.preferredTruck ?? "GT-001";
    final snapshot = await _database.ref('truck_locations').child(truckId).child('status').get();
    if (snapshot.exists) {
      String currentStatus = snapshot.value.toString().toUpperCase();
      if (currentStatus == "OFFLINE") {
        _updateStatus("ACTIVE");
      }
    } else {
      _updateStatus("ACTIVE");
    }
  }

  void _setupListeners() {
    final truckId = _user?.preferredTruck ?? "GT-001";

    _statusSubscription = _database.ref('truck_locations').child(truckId).onValue.listen((event) {
      if (event.snapshot.exists) {
        final data = event.snapshot.value as Map<dynamic, dynamic>;
        if (mounted) {
          setState(() {
            _status = data['status']?.toString().toUpperCase() ?? "OFFLINE";
            _distance = (data['distance'] ?? 0.0).toDouble();
            _visitedCount = (data['visited_puroks'] ?? 0);
            _startTime = data['start_time'] ?? "--:--";
          });
        }
      }
    });

    _puroksSubscription = _database.ref('puroks').onValue.listen((event) {
      if (event.snapshot.exists) {
        try {
          final data = event.snapshot.value;
          final List<Map<dynamic, dynamic>> list = [];

          if (data is Map) {
            data.forEach((key, value) {
              if (value is Map) {
                list.add({...value, 'id': key.toString()});
              } else {
                list.add({'id': key.toString()});
              }
            });
          } else if (data is List) {
            for (int i = 0; i < data.length; i++) {
              if (data[i] != null) {
                if (data[i] is Map) {
                  list.add({...data[i], 'id': 'Purok $i'});
                } else {
                  list.add({'id': data[i].toString()});
                }
              }
            }
          }

          if (mounted) {
            setState(() {
              _puroks = list;
              _isLoadingPuroks = false;
            });
          }
        } catch (e) {
          if (mounted) setState(() => _isLoadingPuroks = false);
        }
      } else {
        if (mounted) setState(() => _isLoadingPuroks = false);
      }
    });
  }

  Future<void> _updateStatus(String newStatus) async {
    final truckId = _user?.preferredTruck ?? "GT-001";
    String now = DateTime.now().toIso8601String();
    
    Map<String, dynamic> updates = {
      'status': newStatus.toLowerCase(),
      'updatedAt': now,
    };

    if (newStatus == "ACTIVE" && (_startTime == "--:--" || _status == "OFFLINE")) {
      String time = DateFormat('h:mm a').format(DateTime.now());
      updates['start_time'] = time;
      if (mounted) setState(() => _startTime = time);
    } else if (newStatus == "OFFLINE") {
      updates['start_time'] = "--:--";
      updates['distance'] = 0.0;
      updates['visited_puroks'] = 0;
      if (mounted) {
        setState(() {
          _startTime = "--:--";
          _distance = 0.0;
          _visitedCount = 0;
        });
      }
    }

    await _database.ref('truck_locations').child(truckId).update(updates);
  }

  StreamSubscription? _statusSubscription;
  StreamSubscription? _puroksSubscription;

  @override
  void dispose() {
    _statusSubscription?.cancel();
    _puroksSubscription?.cancel();
    _headerAnimController.dispose();
    super.dispose();
  }

  List<BoxShadow> get _premiumShadow => [
    BoxShadow(
      color: Colors.black.withAlpha(12),
      blurRadius: 20,
      offset: const Offset(0, 10),
    ),
    BoxShadow(
      color: Colors.black.withAlpha(5),
      blurRadius: 5,
      offset: const Offset(0, 2),
    ),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      body: IndexedStack(
        index: _selectedIndex,
        children: [
          _buildMainDashboard(),
          DriverTrackTruckScreen(
            isEmbedded: true,
            onBack: () => setState(() => _selectedIndex = 0),
          ),
          DriverSettingsScreen(
            isEmbedded: true,
            onBack: () => setState(() => _selectedIndex = 0),
          ),
        ],
      ),
      bottomNavigationBar: _buildBottomNav(),
    );
  }

  Widget _buildMainDashboard() {
    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      child: Column(
        children: [
          _buildAnimatedHeader(),
          const SizedBox(height: 24),
          _buildControlSection(),
          const SizedBox(height: 24),
          _buildStatusCards(),
          const SizedBox(height: 32),
          _buildRouteMapCard(),
          const SizedBox(height: 24),
          _buildPurokListCard(),
          const SizedBox(height: 24),
          _buildTripInfoCard(),
          const SizedBox(height: 24),
          _buildGpsStatusCard(),
          const SizedBox(height: 48),
        ],
      ),
    );
  }

  Widget _buildAnimatedHeader() {
    return Container(
      width: double.infinity,
      height: 280,
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
          BoxShadow(color: Colors.black12, blurRadius: 25, offset: Offset(0, 10)),
        ],
      ),
      child: Stack(
        children: [
          // 🎥 ENHANCED MOVING CIRCLES EFFECT (More circles, more visible)
          AnimatedBuilder(
            animation: _headerAnimController,
            builder: (context, child) {
              return Stack(
                children: [
                  Positioned(
                    top: -40 + (40 * _headerAnimController.value),
                    right: -50 + (60 * _headerAnimController.value),
                    child: Container(
                      width: 220, height: 220,
                      decoration: BoxDecoration(color: Colors.white.withAlpha(40), shape: BoxShape.circle),
                    ),
                  ),
                  Positioned(
                    bottom: 30 - (50 * _headerAnimController.value),
                    left: -60 + (80 * _headerAnimController.value),
                    child: Container(
                      width: 160, height: 160,
                      decoration: BoxDecoration(color: const Color(0xFFE0F2F1).withAlpha(50), shape: BoxShape.circle),
                    ),
                  ),
                  Positioned(
                    top: 100 - (30 * _headerAnimController.value),
                    left: 200 + (20 * _headerAnimController.value),
                    child: Container(
                      width: 100, height: 100,
                      decoration: BoxDecoration(color: Colors.white.withAlpha(25), shape: BoxShape.circle),
                    ),
                  ),
                  Positioned(
                    bottom: 120 + (40 * _headerAnimController.value),
                    right: 150 - (20 * _headerAnimController.value),
                    child: Container(
                      width: 120, height: 120,
                      decoration: BoxDecoration(color: const Color(0xFFB2DFDB).withAlpha(35), shape: BoxShape.circle),
                    ),
                  ),
                ],
              );
            },
          ),

          Padding(
            padding: const EdgeInsets.fromLTRB(28, 64, 28, 0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text(
                      "Driver Dashboard",
                      style: TextStyle(color: AppColors.tealText, fontSize: 14, fontWeight: FontWeight.w900, letterSpacing: 1.2)
                    ),
                    Row(
                      children: [
                        _buildHeaderIcon(Icons.notifications_none_rounded, onTap: () => _showNotificationsModal(context)),
                        const SizedBox(width: 12),
                        _buildHeaderIcon(Icons.logout_rounded, onTap: () => _showLogoutDialog(context)),
                      ],
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Text(
                  _user?.name != null && _user!.name.isNotEmpty ? _user!.name : "Naman",
                  style: const TextStyle(color: AppColors.tealText, fontSize: 40, fontWeight: FontWeight.w900, letterSpacing: -1.0),
                ),
                const SizedBox(height: 4),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                  decoration: BoxDecoration(color: AppColors.tealText.withAlpha(30), borderRadius: BorderRadius.circular(16)),
                  child: Text(
                    "TRUCK: ${_user?.preferredTruck ?? 'GT-001'}",
                    style: const TextStyle(color: AppColors.tealText, fontSize: 12, fontWeight: FontWeight.w900, letterSpacing: 0.8),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHeaderIcon(IconData icon, {VoidCallback? onTap}) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(14),
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: Colors.white.withAlpha(180),
          borderRadius: BorderRadius.circular(14),
          boxShadow: [BoxShadow(color: Colors.black.withAlpha(8), blurRadius: 10, offset: const Offset(0, 4))],
        ),
        child: Icon(icon, color: AppColors.tealText, size: 22),
      ),
    );
  }

  Widget _buildControlSection() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildStatusBadge(),
          const SizedBox(height: 16),
          _buildActionButtons(),
        ],
      ),
    );
  }

  Widget _buildStatusBadge() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        boxShadow: _premiumShadow,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 10, height: 10,
            decoration: BoxDecoration(
              color: _status == "ACTIVE" ? Colors.green : Colors.grey.shade400,
              shape: BoxShape.circle,
            ),
          ),
          const SizedBox(width: 10),
          Text(_status, style: TextStyle(color: Colors.grey.shade600, fontSize: 11, fontWeight: FontWeight.w900, letterSpacing: 1.0)),
        ],
      ),
    );
  }

  Widget _buildActionButtons() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        _buildSmallActionButton("START", Icons.play_arrow_rounded, const Color(0xFF4CAF50), () => _updateStatus("ACTIVE")),
        const SizedBox(width: 12),
        _buildSmallActionButton("PAUSE", Icons.pause_rounded, const Color(0xFFFFA000), () => _updateStatus("IDLE")),
        const SizedBox(width: 12),
        _buildSmallActionButton("FULL", Icons.local_shipping_rounded, const Color(0xFFFF1744), () => _updateStatus("FULL")),
        const SizedBox(width: 12),
        _buildSmallActionButton("DONE", Icons.check_circle_outline_rounded, const Color(0xFF2196F3), () => _updateStatus("OFFLINE")),
      ],
    );
  }

  Widget _buildSmallActionButton(String label, IconData icon, Color color, VoidCallback onTap) {
    return Expanded(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: Container(
          height: 84,
          decoration: BoxDecoration(
            color: color,
            borderRadius: BorderRadius.circular(20),
            boxShadow: [
              BoxShadow(color: color.withAlpha(80), blurRadius: 15, offset: const Offset(0, 8)),
              BoxShadow(color: Colors.black.withAlpha(10), blurRadius: 5, offset: const Offset(0, 2)),
            ],
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, color: Colors.white, size: 26),
              const SizedBox(height: 6),
              Text(label, style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.w900, letterSpacing: 0.5)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStatusCards() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          _buildSquareCard("Manual", "Alert", Icons.notifications_active_rounded, const Color(0xFFFFF3E0), const Color(0xFFE65100)),
          const SizedBox(width: 12),
          _buildSquareCard("Demo", "Simulation", Icons.location_on_rounded, const Color(0xFFF3E5F5), const Color(0xFF7B1FA2)),
          const SizedBox(width: 12),
          _buildSquareCard("$_visitedCount / $_totalPuroks", "Progress", Icons.auto_awesome_rounded, const Color(0xFFE8F5E9), const Color(0xFF2E7D32)),
        ],
      ),
    );
  }

  Widget _buildSquareCard(String title, String subtitle, IconData icon, Color bgColor, Color iconColor) {
    return Expanded(
      child: Container(
        height: 124,
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(28),
          boxShadow: _premiumShadow,
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(color: bgColor, borderRadius: BorderRadius.circular(12)),
              child: Icon(icon, color: iconColor, size: 24),
            ),
            const SizedBox(height: 12),
            Text(title, style: TextStyle(color: iconColor, fontSize: 14, fontWeight: FontWeight.w900), textAlign: TextAlign.center, maxLines: 1, overflow: TextOverflow.ellipsis),
            const SizedBox(height: 2),
            Text(subtitle, style: const TextStyle(color: Color(0xFF9E9E9E), fontSize: 10, fontWeight: FontWeight.w600), textAlign: TextAlign.center),
          ],
        ),
      ),
    );
  }

  Widget _buildRouteMapCard() {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(32),
        boxShadow: _premiumShadow,
      ),
      child: Column(
        children: [
          // HEADER - Interactive
          InkWell(
            onTap: () => setState(() => _selectedIndex = 1),
            borderRadius: const BorderRadius.vertical(top: Radius.circular(32)),
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(color: const Color(0xFFE0F2F1), borderRadius: BorderRadius.circular(10)),
                    child: const Icon(Icons.map_rounded, color: Color(0xFF00BFA5), size: 20),
                  ),
                  const SizedBox(width: 14),
                  const Text("Route Map", style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
                  const Spacer(),
                  Icon(Icons.chevron_right_rounded, color: Colors.grey.shade300),
                ],
              ),
            ),
          ),
          ClipRRect(
            borderRadius: const BorderRadius.vertical(bottom: Radius.circular(32)),
            child: Column(
              children: [
                Container(
                  height: 200, width: double.infinity, color: const Color(0xFFF5F5F5),
                  child: MapWidget(
                    onMapCreated: (map) {},
                    viewport: CameraViewportState(center: Point(coordinates: Position(121.1638, 13.9402)), zoom: 14.0),
                  ),
                ),
                // FOOTER - Interactive
                InkWell(
                  onTap: () => setState(() => _selectedIndex = 1),
                  child: Container(
                    width: double.infinity, padding: const EdgeInsets.symmetric(vertical: 16), color: Colors.white,
                    child: const Center(
                      child: Text(
                        "Tap to view full screen",
                        style: TextStyle(color: Color(0xFF00BFA5), fontSize: 12, fontWeight: FontWeight.w800, letterSpacing: 0.5)
                      )
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPurokListCard() {
    final previewPuroks = _puroks.take(3).toList();
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.symmetric(horizontal: 20),
      padding: const EdgeInsets.all(28),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(36),
        boxShadow: _premiumShadow,
      ),
      child: Column(
        children: [
          if (_isLoadingPuroks)
             const Padding(
               padding: EdgeInsets.symmetric(vertical: 40),
               child: Column(
                 children: [
                   CircularProgressIndicator(strokeWidth: 2, color: Color(0xFF00BFA5)),
                   SizedBox(height: 20),
                   Text("Loading routes...", style: TextStyle(color: Color(0xFF9E9E9E), fontWeight: FontWeight.w600)),
                 ],
               ),
             )
          else if (_puroks.isEmpty)
             const Padding(padding: EdgeInsets.symmetric(vertical: 40), child: Text("No puroks found", style: TextStyle(color: Color(0xFF9E9E9E), fontWeight: FontWeight.w600)))
          else ...[
            ...previewPuroks.map((purok) => _buildPurokItem(purok['id'] ?? "Purok")),
            const SizedBox(height: 12),
            InkWell(
              onTap: () => _showAllPuroksModal(context),
              borderRadius: BorderRadius.circular(12),
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 24),
                child: Text("View All Routes (${_puroks.length})", style: const TextStyle(color: Color(0xFF00BFA5), fontWeight: FontWeight.w900, fontSize: 14, letterSpacing: 0.5)),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildPurokItem(String name) {
    return Container(
      width: double.infinity, margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 20),
      decoration: BoxDecoration(color: const Color(0xFFF8F9FA), borderRadius: BorderRadius.circular(20), border: Border.all(color: const Color(0xFFF1F1F1))),
      child: Row(
        children: [
          Container(padding: const EdgeInsets.all(8), decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(10)), child: const Icon(Icons.location_on_rounded, color: Color(0xFF9E9E9E), size: 18)),
          const SizedBox(width: 18),
          Expanded(child: Text(name, style: const TextStyle(fontWeight: FontWeight.w800, color: Color(0xFF2C3E50), fontSize: 16))),
        ],
      ),
    );
  }

  Widget _buildTripInfoCard() {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 20),
      padding: const EdgeInsets.all(28),
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(32), boxShadow: _premiumShadow),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text("Trip Statistics", style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
          const SizedBox(height: 24),
          _buildInfoRow("Assigned Truck", _user?.preferredTruck ?? "GT-001", color: const Color(0xFF00BFA5)),
          const Padding(padding: EdgeInsets.symmetric(vertical: 12), child: Divider(height: 1, color: Color(0xFFF5F5F5))),
          _buildInfoRow("Start Time", _startTime),
          const Padding(padding: EdgeInsets.symmetric(vertical: 12), child: Divider(height: 1, color: Color(0xFFF5F5F5))),
          _buildInfoRow("Distance Covered", "${_distance.toStringAsFixed(1)} km", color: const Color(0xFF2E7D32)),
        ],
      ),
    );
  }

  Widget _buildGpsStatusCard() {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 20),
      padding: const EdgeInsets.all(28),
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(32), boxShadow: _premiumShadow),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text("GPS Connectivity", style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
          const SizedBox(height: 24),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
            decoration: BoxDecoration(
              color: const Color(0xFFE8F5E9),
              borderRadius: BorderRadius.circular(16),
            ),
            child: const Row(
              children: [
                Text("Connection", style: TextStyle(color: Color(0xFF2E7D32), fontWeight: FontWeight.w700)),
                Spacer(),
                Icon(Icons.circle, color: Color(0xFF4CAF50), size: 10),
                SizedBox(width: 8),
                Text("Strong Signal", style: TextStyle(color: Color(0xFF2E7D32), fontWeight: FontWeight.w900)),
              ],
            ),
          ),
          const SizedBox(height: 16),
          _buildInfoRow("Accuracy", "±5 meters"),
          const Padding(padding: EdgeInsets.symmetric(vertical: 12), child: Divider(height: 1, color: Color(0xFFF5F5F5))),
          _buildInfoRow("Last Refresh", DateFormat('h:mm:ss a').format(DateTime.now())),
        ],
      ),
    );
  }

  Widget _buildInfoRow(String label, String value, {Color? color}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: const TextStyle(color: Color(0xFF9E9E9E), fontWeight: FontWeight.w600, fontSize: 13)),
        Text(value, style: TextStyle(fontWeight: FontWeight.w900, color: color ?? const Color(0xFF1A1A1A), fontSize: 14)),
      ],
    );
  }

  Widget _buildBottomNav() {
    return Container(
      decoration: BoxDecoration(color: Colors.white, boxShadow: [BoxShadow(color: Colors.black.withAlpha(8), blurRadius: 30, offset: const Offset(0, -10))]),
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _buildNavItem(Icons.home_rounded, 'Dashboard', 0),
              _buildNavItem(Icons.location_on_rounded, 'Map', 1),
              _buildNavItem(Icons.settings_rounded, 'Settings', 2),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildNavItem(IconData icon, String label, int index) {
    bool isSelected = _selectedIndex == index;
    return GestureDetector(
      onTap: () => setState(() => _selectedIndex = index),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          AnimatedContainer(
            duration: const Duration(milliseconds: 300),
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 10),
            decoration: BoxDecoration(color: isSelected ? const Color(0xFFE0F2F1) : Colors.transparent, borderRadius: BorderRadius.circular(24)),
            child: Icon(icon, size: 26, color: isSelected ? const Color(0xFF00BFA5) : const Color(0xFFBDBDBD)),
          ),
          const SizedBox(height: 4),
          Text(label, style: TextStyle(fontSize: 11, fontWeight: isSelected ? FontWeight.w900 : FontWeight.w600, color: isSelected ? const Color(0xFF00BFA5) : const Color(0xFFBDBDBD), letterSpacing: 0.5)),
        ],
      ),
    );
  }

  void _showNotificationsModal(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => Dialog(
        backgroundColor: Colors.white, surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(36)),
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Container(padding: const EdgeInsets.all(10), decoration: BoxDecoration(color: const Color(0xFFE0F2F1), borderRadius: BorderRadius.circular(12)), child: const Icon(Icons.notifications_active_rounded, color: Color(0xFF00BFA5), size: 24)),
                      const SizedBox(width: 14),
                      const Text("Updates", style: TextStyle(fontSize: 22, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
                    ],
                  ),
                  TextButton(onPressed: () {}, child: const Text("Clear", style: TextStyle(color: Color(0xFF00BFA5), fontWeight: FontWeight.w900))),
                ],
              ),
              const SizedBox(height: 40),
              Column(
                children: [
                  Container(width: 72, height: 72, decoration: BoxDecoration(color: const Color(0xFFF8F9FA), shape: BoxShape.circle, border: Border.all(color: const Color(0xFFF1F1F1))), child: const Icon(Icons.done_all_rounded, color: Color(0xFFBDBDBD), size: 32)),
                  const SizedBox(height: 24),
                  const Text("No New Alerts", style: TextStyle(fontSize: 16, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
                  const SizedBox(height: 8),
                  const Text("We'll notify you here.", style: TextStyle(color: Color(0xFF9E9E9E), fontSize: 13, fontWeight: FontWeight.w500)),
                ],
              ),
              const SizedBox(height: 48),
              ElevatedButton(
                onPressed: () => Navigator.pop(context),
                style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFFF5F5F5), foregroundColor: const Color(0xFF1A1A1A), elevation: 0, minimumSize: const Size(double.infinity, 56), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16))),
                child: const Text("CLOSE", style: TextStyle(fontWeight: FontWeight.w900, fontSize: 14, letterSpacing: 1.2)),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showLogoutDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => Dialog(
        backgroundColor: Colors.white, surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(36)),
        child: Padding(
          padding: const EdgeInsets.all(40),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(padding: const EdgeInsets.all(22), decoration: const BoxDecoration(color: Color(0xFFF5F5F5), shape: BoxShape.circle), child: const Icon(Icons.power_settings_new_rounded, color: Color(0xFF1A1A1A), size: 36)),
              const SizedBox(height: 32),
              const Text("Sign Out?", style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
              const SizedBox(height: 16),
              const Text("Are you ready to end your shift and secure your account?", textAlign: TextAlign.center, style: TextStyle(color: Color(0xFF757575), fontSize: 14, height: 1.6, fontWeight: FontWeight.w500)),
              const SizedBox(height: 40),
              ElevatedButton(
                onPressed: () async { await _updateStatus("OFFLINE"); await SessionManager.logout(); if (!mounted) return; Navigator.pushReplacementNamed(context, '/'); },
                style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF00BFA5), minimumSize: const Size(double.infinity, 64), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)), elevation: 8, shadowColor: const Color(0xFF00BFA5).withAlpha(80)),
                child: const Text("YES, SIGN OUT", style: TextStyle(color: Colors.white, fontSize: 15, fontWeight: FontWeight.w900, letterSpacing: 1)),
              ),
              const SizedBox(height: 16),
              TextButton(onPressed: () => Navigator.pop(context), child: const Text("CANCEL", style: TextStyle(color: Color(0xFFBDBDBD), fontWeight: FontWeight.w900, letterSpacing: 1.2))),
            ],
          ),
        ),
      ),
    );
  }

  void _showAllPuroksModal(BuildContext context) {
    showModalBottomSheet(
      context: context, isScrollControlled: true, backgroundColor: Colors.transparent,
      builder: (context) => Container(
        height: MediaQuery.of(context).size.height * 0.85,
        decoration: const BoxDecoration(color: Colors.white, borderRadius: BorderRadius.vertical(top: Radius.circular(40))),
        padding: const EdgeInsets.all(32),
        child: Column(
          children: [
            Row(
              children: [
                Container(padding: const EdgeInsets.all(12), decoration: BoxDecoration(color: const Color(0xFFE0F2F1), borderRadius: BorderRadius.circular(16)), child: const Icon(Icons.route_rounded, color: Color(0xFF00BFA5), size: 28)),
                const SizedBox(width: 18),
                const Text("Collection Path", style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
              ],
            ),
            const SizedBox(height: 12),
            const Align(alignment: Alignment.centerLeft, child: Text("Your assigned schedule for today", style: TextStyle(color: Color(0xFF757575), fontSize: 14, fontWeight: FontWeight.w600))),
            const SizedBox(height: 32),
            Expanded(
              child: _puroks.isEmpty
                ? const Center(child: Text("No routes available"))
                : ListView.separated(
                    itemCount: _puroks.length,
                    separatorBuilder: (context, index) => const Divider(height: 1, color: Color(0xFFF5F5F5)),
                    itemBuilder: (context, index) {
                      final purok = _puroks[index];
                      return Padding(padding: const EdgeInsets.symmetric(vertical: 22), child: Text(purok['id'] ?? "Purok", style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: Color(0xFF1A1A1A))));
                    },
                  ),
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: () => Navigator.pop(context),
              style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFFF5F5F5), foregroundColor: const Color(0xFF1A1A1A), elevation: 0, minimumSize: const Size(double.infinity, 64), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20))),
              child: const Text("CLOSE", style: TextStyle(fontWeight: FontWeight.w900, fontSize: 15, letterSpacing: 1.2)),
            ),
          ],
        ),
      ),
    );
  }
}
