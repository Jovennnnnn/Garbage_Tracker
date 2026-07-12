import 'package:flutter/material.dart';
import 'package:firebase_database/firebase_database.dart';
import 'package:mapbox_maps_flutter/mapbox_maps_flutter.dart' hide Size;
import '../utils/app_theme.dart';

class TrackTrucksScreen extends StatefulWidget {
  final bool isEmbedded;
  final VoidCallback? onBack;
  const TrackTrucksScreen({super.key, this.isEmbedded = false, this.onBack});

  @override
  State<TrackTrucksScreen> createState() => _TrackTrucksScreenState();
}

class _TrackTrucksScreenState extends State<TrackTrucksScreen> {
  final FirebaseDatabase _database = FirebaseDatabase.instance;
  MapboxMap? mapboxMap;
  List<Map<dynamic, dynamic>> _trucks = [];
  final Set<String> _comparedTrucks = {};
  
  // Point Annotation Manager for truck icons
  PointAnnotationManager? _pointAnnotationManager;

  @override
  void initState() {
    super.initState();
    _listenToTrucks();
  }

  void _listenToTrucks() {
    _database.ref('truck_locations').onValue.listen((event) {
      if (event.snapshot.exists) {
        final Map data = event.snapshot.value as Map;
        final List<Map<dynamic, dynamic>> list = [];
        data.forEach((key, value) {
          list.add(value as Map<dynamic, dynamic>);
        });
        if (mounted) {
          setState(() => _trucks = list);
          _updateTruckMarkers();
        }
      }
    });
  }

  void _onMapCreated(MapboxMap map) {
    mapboxMap = map;
  }

  void _onStyleLoaded(dynamic data) {
    mapboxMap?.annotations.createPointAnnotationManager().then((manager) {
      _pointAnnotationManager = manager;
      _updateTruckMarkers();
    });
  }

  void _updateTruckMarkers() {
    if (_pointAnnotationManager == null || _trucks.isEmpty) return;

    _pointAnnotationManager?.deleteAll();
    
    for (var truck in _trucks) {
      final double lat = (truck['latitude'] ?? 13.9402).toDouble();
      final double lng = (truck['longitude'] ?? 121.1638).toDouble();
      final String id = truck['truck_id'] ?? "GT-001";

      _pointAnnotationManager?.create(
        PointAnnotationOptions(
          geometry: Point(coordinates: Position(lng, lat)),
          textField: id,
          textOffset: [0, 2],
          textColor: Colors.blue.value,
          iconImage: "truck-15", // Built-in Mapbox icon
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      body: Stack(
        children: [
          // 🗺️ MAP AREA - Now with real Mapbox!
          Positioned.fill(
            child: MapWidget(
              onMapCreated: _onMapCreated,
              onStyleLoadedListener: _onStyleLoaded,
              viewport: CameraViewportState(
                center: Point(coordinates: Position(121.1638, 13.9402)),
                zoom: 14.0,
              ),
            ),
          ),

          // 🏗️ HEADER (Image 1)
          Positioned(
            top: 0,
            left: 0,
            right: 0,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: const BoxDecoration(
                color: Colors.white,
                boxShadow: [BoxShadow(color: Colors.black12, blurRadius: 4, offset: Offset(0, 2))],
              ),
              child: SafeArea(
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
                      const SizedBox(width: 12),
                    ],
                    const Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text("Track Trucks", style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
                        Text("Real-time GPS locations", style: TextStyle(fontSize: 12, color: Color(0xFF757575), fontWeight: FontWeight.w500)),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),

          // 📈 ROUTE PROGRESS
          Positioned(
            top: widget.isEmbedded ? 68 : 96,
            left: 0,
            right: 0,
            child: LinearProgressIndicator(
              value: 0.45,
              backgroundColor: const Color(0xFFE0E0E0),
              valueColor: const AlwaysStoppedAnimation(Color(0xFF2196F3)),
              minHeight: 4,
            ),
          ),

          // 📄 SLIDING BOTTOM PANEL (Image 1, 2, 3)
          Positioned.fill(
            child: DraggableScrollableSheet(
              initialChildSize: 0.45,
              minChildSize: 0.18,
              maxChildSize: 0.95,
              snap: true,
              snapSizes: const [0.18, 0.45, 0.95],
              builder: (context, scrollController) {
                return Container(
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: const BorderRadius.vertical(top: Radius.circular(40)),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withAlpha(20),
                        blurRadius: 20,
                        spreadRadius: 5,
                        offset: const Offset(0, -5),
                      ),
                    ],
                  ),
                  child: SingleChildScrollView(
                    controller: scrollController,
                    physics: const ClampingScrollPhysics(),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const SizedBox(height: 12),
                        // Professional Handle Bar
                        Center(
                          child: Container(
                            width: 50,
                            height: 6,
                            decoration: BoxDecoration(
                              color: const Color(0xFFE0E0E0),
                              borderRadius: BorderRadius.circular(10),
                            ),
                          ),
                        ),
                        const SizedBox(height: 24),
    
                        // Title Section
                        const Padding(
                          padding: EdgeInsets.symmetric(horizontal: 28),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                "Fleet Status",
                                style: TextStyle(
                                  fontSize: 24,
                                  fontWeight: FontWeight.w900,
                                  color: Color(0xFF1A1A1A),
                                  letterSpacing: -0.5,
                                ),
                              ),
                              Icon(Icons.keyboard_arrow_up_rounded, color: Colors.grey),
                            ],
                          ),
                        ),
                        const SizedBox(height: 20),
    
                        // Truck List
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 20),
                          child: Column(
                            children: _trucks.isEmpty
                              ? [const Padding(padding: EdgeInsets.all(60), child: Center(child: Text("Scanning for active units...", style: TextStyle(color: Colors.grey, fontWeight: FontWeight.w500))))]
                              : _trucks.map((truck) => _buildDetailedTruckCard(truck)).toList(),
                          ),
                        ),
    
                        const SizedBox(height: 24),
    
                        // Fleet Management Guide (Image 3)
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 24),
                          child: Container(
                            padding: const EdgeInsets.all(28),
                            decoration: BoxDecoration(
                              color: const Color(0xFFF8F9FA),
                              borderRadius: BorderRadius.circular(32),
                              border: Border.all(color: const Color(0xFFF0F0F0)),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const Text("Fleet Management Guide", style: TextStyle(fontWeight: FontWeight.w900, fontSize: 16, color: Color(0xFF1A1A1A))),
                                const SizedBox(height: 20),
                                _buildGuideRow("Tap 'History' to view detailed audit trails"),
                                _buildGuideRow("Use 'Compare Path' for AI-optimized routes"),
                                _buildGuideRow("Real-time heatmaps indicate collection speed"),
                              ],
                            ),
                          ),
                        ),
                        const SizedBox(height: 120), // Extra space for bottom nav clearance
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildGuideRow(String text) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text("• ", style: TextStyle(color: Color(0xFF757575), fontWeight: FontWeight.w900)),
          Expanded(child: Text(text, style: const TextStyle(fontSize: 13, color: Color(0xFF757575), fontWeight: FontWeight.w500))),
        ],
      ),
    );
  }

  Widget _buildDetailedTruckCard(Map<dynamic, dynamic> truck) {
    String id = truck['truck_id'] ?? "GT-001";
    String status = (truck['status'] ?? "Idle").toString().toUpperCase();
    String driver = truck['driver_name'] ?? "Steve Espaldon";
    String location = truck['purok'] ?? "Balintawak";
    String speed = "${truck['speed'] ?? 0} km/h";

    bool isCompared = _comparedTrucks.contains(id);
    Color statusColor = status == 'ACTIVE' ? const Color(0xFF4CAF50) : (status == 'FULL' ? const Color(0xFFFF1744) : const Color(0xFFFFAB00));

    return Container(
      margin: const EdgeInsets.only(bottom: 20),
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(32),
        boxShadow: [BoxShadow(color: Colors.black.withAlpha(5), blurRadius: 10, offset: const Offset(0, 4))],
        border: Border.all(color: const Color(0xFFF5F5F5)),
      ),
      child: Column(
        children: [
          // Header Row
          Row(
            children: [
              Container(
                width: 52,
                height: 52,
                decoration: BoxDecoration(color: const Color(0xFFE3F2FD), borderRadius: BorderRadius.circular(16)),
                child: const Icon(Icons.local_shipping_rounded, color: Color(0xFF1976D2), size: 28),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(id, style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 18, color: Color(0xFF1A1A1A))),
                    const Text("N/A", style: TextStyle(color: Color(0xFFBDBDBD), fontSize: 13, fontWeight: FontWeight.w500)),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                decoration: BoxDecoration(color: statusColor.withAlpha(30), borderRadius: BorderRadius.circular(12)),
                child: Text(status, style: TextStyle(color: statusColor, fontSize: 11, fontWeight: FontWeight.w900)),
              ),
            ],
          ),
          const SizedBox(height: 24),

          // Info Grid
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              _buildInfoItem(Icons.location_on_rounded, const Color(0xFFFF1744), "Location", location),
              _buildInfoItem(Icons.refresh_rounded, const Color(0xFF03A9F4), "Speed", speed),
              _buildInfoItem(Icons.person_rounded, const Color(0xFF1976D2), "Driver", driver),
            ],
          ),
          const SizedBox(height: 24),

          // Stats Summary (Image 2)
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(color: const Color(0xFFF8F9FA), borderRadius: BorderRadius.circular(20)),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _buildStatItem(Icons.local_shipping_outlined, "DISTANCE", "3.4 km", Color(0xFF2E7D32)),
                _buildStatItem(Icons.local_gas_station_outlined, "FUEL", "0.7 L", Color(0xFFD32F2F)),
                _buildStatItem(Icons.radio_button_checked_rounded, "STOPS", "2", Color(0xFFD32F2F)),
              ],
            ),
          ),
          const SizedBox(height: 24),

          // Action Buttons
          Row(
            children: [
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: () {},
                  icon: const Icon(Icons.location_on_rounded, size: 20),
                  label: const Text("HISTORY", style: TextStyle(fontWeight: FontWeight.w900)),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFFF0F4F8),
                    foregroundColor: const Color(0xFF1A1A1A),
                    elevation: 0,
                    minimumSize: const Size(0, 56),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: () {
                    setState(() {
                      if (isCompared) _comparedTrucks.remove(id);
                      else _comparedTrucks.add(id);
                    });
                  },
                  icon: Icon(isCompared ? Icons.visibility_off_rounded : Icons.navigation_rounded, size: 20),
                  label: Text(isCompared ? "HIDE PATH" : "COMPARE PATH", style: const TextStyle(fontWeight: FontWeight.w900)),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF2E7D32),
                    foregroundColor: Colors.white,
                    elevation: 0,
                    minimumSize: const Size(0, 56),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),

          // Footer Info
          Row(
            children: [
              const Icon(Icons.access_time_rounded, size: 16, color: Color(0xFFBDBDBD)),
              const SizedBox(width: 4),
              const Text("Last Update:", style: TextStyle(fontSize: 12, color: Color(0xFFBDBDBD), fontWeight: FontWeight.w500)),
              const Spacer(),
              const Text("ETA: 32 mins", style: TextStyle(fontSize: 12, color: Color(0xFF1E88E5), fontWeight: FontWeight.w900)),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildInfoItem(IconData icon, Color color, String label, String value) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 14, color: color),
              const SizedBox(width: 4),
              Text(label, style: const TextStyle(fontSize: 11, color: Color(0xFFBDBDBD), fontWeight: FontWeight.w600)),
            ],
          ),
          const SizedBox(height: 4),
          Text(value, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
        ],
      ),
    );
  }

  static Widget _buildStatItem(IconData icon, String label, String value, Color color) {
    return Column(
      children: [
        Row(
          children: [
            Icon(icon, size: 12, color: const Color(0xFF757575)),
            const SizedBox(width: 4),
            Text(label, style: const TextStyle(fontSize: 9, color: Color(0xFF757575), fontWeight: FontWeight.w800)),
          ],
        ),
        const SizedBox(height: 4),
        Text(value, style: TextStyle(fontSize: 14, fontWeight: FontWeight.w900, color: color)),
      ],
    );
  }
}
