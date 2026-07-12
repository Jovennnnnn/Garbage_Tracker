import 'package:flutter/material.dart';
import 'package:firebase_database/firebase_database.dart';
import 'package:mapbox_maps_flutter/mapbox_maps_flutter.dart' hide Size;
import 'package:intl/intl.dart';
import '../utils/app_theme.dart';
import '../utils/session_manager.dart';
import '../models/user.dart';

class DriverTrackTruckScreen extends StatefulWidget {
  final bool isEmbedded;
  final VoidCallback? onBack;
  const DriverTrackTruckScreen({super.key, this.isEmbedded = false, this.onBack});

  @override
  State<DriverTrackTruckScreen> createState() => _DriverTrackTruckScreenState();
}

class _DriverTrackTruckScreenState extends State<DriverTrackTruckScreen> {
  final FirebaseDatabase _database = FirebaseDatabase.instance;
  MapboxMap? mapboxMap;
  UserData? _user;
  Map<dynamic, dynamic>? _truckData;
  
  PointAnnotationManager? _pointAnnotationManager;

  @override
  void initState() {
    super.initState();
    _loadUser();
  }

  void _loadUser() async {
    _user = await SessionManager.getUser();
    if (mounted) setState(() {});
    _listenToTruckData();
  }

  void _listenToTruckData() {
    final truckId = _user?.preferredTruck ?? "GT-001";
    _database.ref('truck_locations').child(truckId).onValue.listen((event) {
      if (event.snapshot.exists) {
        final Map data = event.snapshot.value as Map;
        if (mounted) {
          setState(() => _truckData = data);
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
    if (_pointAnnotationManager == null || _truckData == null) return;
    _pointAnnotationManager?.deleteAll();

    final double lat = (_truckData?['latitude'] ?? 13.9402).toDouble();
    final double lng = (_truckData?['longitude'] ?? 121.1638).toDouble();
    final String id = _truckData?['truck_id'] ?? "GT-001";

    _pointAnnotationManager?.create(
      PointAnnotationOptions(
        geometry: Point(coordinates: Position(lng, lat)),
        textField: id,
        textOffset: [0, 2],
        textColor: Colors.blue.toARGB32(),
        iconImage: "truck-15",
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      body: Stack(
        children: [
          // 🗺️ MAP AREA - Layered behind modal
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

          // 🏛️ SYNCED HEADER (Matches Resident Track Screen Style)
          Positioned(
            top: 0, left: 0, right: 0,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: const BoxDecoration(
                color: Colors.white,
                boxShadow: [BoxShadow(color: Colors.black12, blurRadius: 4, offset: Offset(0, 2))]
              ),
              child: SafeArea(
                child: Row(
                  children: [
                    if (!widget.isEmbedded || widget.onBack != null)
                      IconButton(
                        icon: const Icon(Icons.arrow_back_ios_new_rounded, color: Color(0xFF1A1A1A), size: 20),
                        onPressed: () {
                          if (widget.onBack != null) {
                            widget.onBack!();
                          } else {
                            Navigator.pop(context);
                          }
                        },
                      ),
                    const SizedBox(width: 8),
                    const Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text("Track Fleet", style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
                        Text("Active GPS signal connected", style: TextStyle(fontSize: 11, color: Color(0xFF757575), fontWeight: FontWeight.w600)),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),

          // 🛝 MODAL - Live Route Tracking
          Positioned.fill(
            child: DraggableScrollableSheet(
              initialChildSize: 0.35,
              minChildSize: 0.2,
              maxChildSize: 0.8,
              snap: true,
              snapSizes: const [0.2, 0.35, 0.8],
              builder: (context, scrollController) {
                return Container(
                  decoration: const BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.vertical(top: Radius.circular(44)),
                    boxShadow: [
                      BoxShadow(color: Colors.black12, blurRadius: 30, offset: Offset(0, -10)),
                    ],
                  ),
                  child: SingleChildScrollView(
                    controller: scrollController,
                    padding: const EdgeInsets.symmetric(horizontal: 24),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const SizedBox(height: 12),
                        // Handle
                        Center(
                          child: Container(
                            width: 48, height: 5,
                            decoration: BoxDecoration(color: Colors.grey.shade200, borderRadius: BorderRadius.circular(10)),
                          ),
                        ),
                        const SizedBox(height: 36),
                        const Text(
                          "Live Route Tracking",
                          style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A), letterSpacing: -0.5),
                        ),
                        const Text(
                          "Searching for nearby collection points...",
                          style: TextStyle(fontSize: 14, color: Color(0xFF9E9E9E), fontWeight: FontWeight.w600),
                        ),
                        const SizedBox(height: 40),

                        // 📦 TRIP INFORMATION CARD
                        _buildTripInfoCard(),
                        const SizedBox(height: 24),

                        // 📡 GPS STATUS CARD (Now matching Teal/Green theme)
                        _buildGpsStatusCard(),
                        const SizedBox(height: 150),
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

  Widget _buildTripInfoCard() {
    return Container(
      padding: const EdgeInsets.all(28),
      decoration: BoxDecoration(
        color: const Color(0xFFE0F7FA).withAlpha(150), // Standard Teal Background
        borderRadius: BorderRadius.circular(36),
        boxShadow: [BoxShadow(color: Colors.black.withAlpha(8), blurRadius: 15, offset: const Offset(0, 8))],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(10)),
                child: const Icon(Icons.info_outline_rounded, color: Color(0xFF00BFA5), size: 20),
              ),
              const SizedBox(width: 14),
              const Text("Trip Information", style: TextStyle(fontWeight: FontWeight.w900, color: Color(0xFF2C3E50), fontSize: 17)),
            ],
          ),
          const SizedBox(height: 28),
          _buildDetailRow("Truck Number", _user?.preferredTruck ?? "GT-001", isHighlight: true),
          const Padding(padding: EdgeInsets.symmetric(vertical: 14), child: Divider(height: 1, color: Colors.black12)),
          _buildDetailRow("Plate Number", "ABC 1234"),
          const Padding(padding: EdgeInsets.symmetric(vertical: 14), child: Divider(height: 1, color: Colors.black12)),
          _buildDetailRow("Start Time", _truckData?['start_time'] ?? "10:34 pm"),
          const Padding(padding: EdgeInsets.symmetric(vertical: 14), child: Divider(height: 1, color: Colors.black12)),
          _buildDetailRow("Total Distance", "${(_truckData?['distance'] ?? 0.0).toStringAsFixed(1)} km", valColor: Colors.teal.shade800),
        ],
      ),
    );
  }

  Widget _buildGpsStatusCard() {
    return Container(
      padding: const EdgeInsets.all(28),
      decoration: BoxDecoration(
        color: const Color(0xFFE0F7FA).withAlpha(150), // CHANGED TO TEAL/GREEN
        borderRadius: BorderRadius.circular(36),
        boxShadow: [BoxShadow(color: Colors.black.withAlpha(8), blurRadius: 15, offset: const Offset(0, 8))],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(10)),
                child: const Icon(Icons.satellite_alt_rounded, color: Color(0xFF00BFA5), size: 20),
              ),
              const SizedBox(width: 14),
              const Text("GPS Connectivity", style: TextStyle(fontWeight: FontWeight.w900, color: Color(0xFF2C3E50), fontSize: 17)),
            ],
          ),
          const SizedBox(height: 28),
          _buildWhiteInfoRow("Signal Strength", status: "Strong"),
          const SizedBox(height: 14),
          _buildWhiteInfoRow("Accuracy", val: "±5 meters"),
          const SizedBox(height: 14),
          _buildWhiteInfoRow("Current Speed", val: "${_truckData?['speed'] ?? 0} km/h"),
        ],
      ),
    );
  }

  Widget _buildDetailRow(String label, String val, {bool isHighlight = false, Color? valColor}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: const TextStyle(color: Color(0xFF757575), fontWeight: FontWeight.w700, fontSize: 15)),
        Text(
          val,
          style: TextStyle(
            fontWeight: FontWeight.w900,
            color: valColor ?? (isHighlight ? const Color(0xFF00BFA5) : const Color(0xFF1A1A1A)),
            fontSize: 15
          )
        ),
      ],
    );
  }

  Widget _buildWhiteInfoRow(String label, {String? val, String? status}) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(24),
        boxShadow: [BoxShadow(color: Colors.black.withAlpha(5), blurRadius: 10, offset: const Offset(0, 4))],
      ),
      child: Row(
        children: [
          Text(label, style: const TextStyle(color: Color(0xFF757575), fontWeight: FontWeight.w700, fontSize: 15)),
          const Spacer(),
          if (status != null) ...[
            const Icon(Icons.circle, color: Color(0xFF4CAF50), size: 10),
            const SizedBox(width: 8),
            Text(status, style: const TextStyle(color: Color(0xFF00BFA5), fontWeight: FontWeight.w900, fontSize: 15)),
          ] else ...[
            Text(val ?? "", style: const TextStyle(fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A), fontSize: 15)),
          ],
        ],
      ),
    );
  }
}
