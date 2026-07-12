import 'package:flutter/material.dart';
import 'package:firebase_database/firebase_database.dart';
import '../api/api_service.dart';

class ComplaintsScreen extends StatefulWidget {
  final bool isEmbedded;
  final VoidCallback? onBack;
  const ComplaintsScreen({super.key, this.isEmbedded = false, this.onBack});

  @override
  State<ComplaintsScreen> createState() => _ComplaintsScreenState();
}

class _ComplaintsScreenState extends State<ComplaintsScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final FirebaseDatabase _database = FirebaseDatabase.instance;
  final ApiService _apiService = ApiService();

  List<dynamic> _residentComplaints = [];
  List<dynamic> _driverIssues = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _tabController.addListener(() {
      if (mounted) setState(() {});
    });
    _fetchData();
  }

  Future<void> _fetchData() async {
    setState(() => _isLoading = true);
    try {
      // Fetch Resident Complaints from MySQL
      final complaintResponse = await _apiService.getComplaints();
      final List complaints = complaintResponse.data['data'] ?? [];

      // Fetch Driver Issues from Firebase
      final issueSnapshot = await _database.ref('notifications').get();

      final List issues = [];
      if (issueSnapshot.exists) {
        final Map data = issueSnapshot.value as Map;
        data.forEach((key, value) {
          if (value['type'] == 'DRIVER_ISSUE') {
            issues.add({...Map<String, dynamic>.from(value as Map), 'id': key});
          }
        });
        // Sort by timestamp descending
        issues.sort((a, b) => (b['timestamp'] ?? "").compareTo(a['timestamp'] ?? ""));
      }

      if (mounted) {
        setState(() {
          _residentComplaints = complaints;
          _driverIssues = issues;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isLoading = false);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text("Error: $e")));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    String title = _tabController.index == 0 ? "Resident Complaints" : "Driver Issues";
    int count = _tabController.index == 0 ? _residentComplaints.length : _driverIssues.length;

    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(title, count),
            _buildTabBar(),
            Expanded(
              child: _isLoading
                  ? const Center(child: CircularProgressIndicator(color: Color(0xFF00BFA5)))
                  : TabBarView(
                      controller: _tabController,
                      children: [
                        _buildMainList(_residentComplaints, true),
                        _buildMainList(_driverIssues, false),
                      ],
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(String title, int count) {
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
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
                Text("$count total", style: const TextStyle(fontSize: 12, color: Color(0xFF757575), fontWeight: FontWeight.w500)),
              ],
            ),
          ),
          const Icon(Icons.file_download_outlined, color: Color(0xFF00BFA5), size: 28),
        ],
      ),
    );
  }

  Widget _buildTabBar() {
    return Container(
      color: Colors.white,
      child: TabBar(
        controller: _tabController,
        tabs: const [
          Tab(text: "Complaints"),
          Tab(text: "Driver Issues"),
        ],
        labelColor: const Color(0xFF00BFA5),
        unselectedLabelColor: const Color(0xFF757575),
        indicatorColor: const Color(0xFF00BFA5),
        indicatorWeight: 3,
        labelStyle: const TextStyle(fontWeight: FontWeight.w900, fontSize: 14),
        unselectedLabelStyle: const TextStyle(fontWeight: FontWeight.w500, fontSize: 14),
      ),
    );
  }

  Widget _buildMainList(List<dynamic> items, bool isResident) {
    String normalize(dynamic s) {
      if (s == null) return 'PENDING';
      String str = s.toString().toUpperCase().trim();
      // Handle "IN PROGRESS" with any number of spaces or underscores
      if (str.contains('IN') && str.contains('PROGRESS')) return 'IN_PROGRESS';
      if (str == 'RESOLVED' || str == 'COMPLETED') return 'RESOLVED';
      return 'PENDING';
    }
    
    int pending = items.where((i) => normalize(i['status']) == 'PENDING').length;
    int inProgress = items.where((i) => normalize(i['status']) == 'IN_PROGRESS').length;
    int resolved = items.where((i) => normalize(i['status']) == 'RESOLVED').length;

    return RefreshIndicator(
      onRefresh: _fetchData,
      color: const Color(0xFF00BFA5),
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
        children: [
          // 📊 STAT CARDS
          Row(
            children: [
              Expanded(child: _buildStatCard(pending.toString(), "Pending", Icons.access_time_rounded, const Color(0xFFFFA000), const Color(0xFFFFF8E1))),
              const SizedBox(width: 12),
              Expanded(child: _buildStatCard(inProgress.toString(), "In Progress", Icons.chat_bubble_outline_rounded, const Color(0xFF1976D2), const Color(0xFFE3F2FD))),
              const SizedBox(width: 12),
              Expanded(child: _buildStatCard(resolved.toString(), "Resolved", Icons.check_rounded, const Color(0xFF2E7D32), const Color(0xFFE8F5E9))),
            ],
          ),
          const SizedBox(height: 32),

          // 📄 LIST ITEMS
          if (items.isEmpty)
            const Center(child: Padding(padding: EdgeInsets.only(top: 40), child: Text("No reports found", style: TextStyle(color: Colors.grey))))
          else
            ...items.map((item) => _buildIssueCard(item, isResident)).toList(),

          const SizedBox(height: 24),
          const Center(child: Text("View All", style: TextStyle(color: Color(0xFF1976D2), fontWeight: FontWeight.w900, fontSize: 14))),
          const SizedBox(height: 40),
        ],
      ),
    );
  }

  Widget _buildStatCard(String value, String label, IconData icon, Color color, Color bgColor) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 20),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(24),
        boxShadow: [BoxShadow(color: Colors.black.withAlpha(5), blurRadius: 10, offset: const Offset(0, 4))],
      ),
      child: Column(
        children: [
          Icon(icon, color: color, size: 24),
          const SizedBox(height: 12),
          Text(value, style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900, color: color)),
          Text(label, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w800, color: color)),
        ],
      ),
    );
  }

  Widget _buildIssueCard(dynamic item, bool isResident) {
    String category = isResident ? (item['category'] ?? "General") : (item['title']?.toString().replaceAll("New Driver Issue: ", "") ?? "Vehicle Issue");
    String name = isResident ? (item['full_name'] ?? "Resident") : (item['driver_name'] ?? "User");
    String purok = item['purok'] ?? (item['location'] ?? "Sentro");
    String description = isResident ? (item['description'] ?? "") : (item['message'] ?? "");
    String timestamp = item['created_at'] ?? item['timestamp'] ?? "2026-05-17 13:42:06";
    
    String rawStatus = (item['status'] ?? 'pending').toString().toLowerCase().trim();
    String status = 'PENDING';
    if (rawStatus == 'in_progress' || (rawStatus.contains('in') && rawStatus.contains('progress'))) {
      status = 'IN_PROGRESS';
    } else if (rawStatus == 'resolved' || rawStatus == 'completed') {
      status = 'RESOLVED';
    }

    Color statusColor = status == 'RESOLVED' ? const Color(0xFF4CAF50) : (status == 'IN_PROGRESS' ? const Color(0xFF1E88E5) : const Color(0xFFFFA000));
    Color statusBg = statusColor.withAlpha(30);

    return Container(
      margin: const EdgeInsets.only(bottom: 20),
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(28),
        boxShadow: [BoxShadow(color: Colors.black.withAlpha(5), blurRadius: 10, offset: const Offset(0, 4))],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(child: Text(category, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A)))),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(color: statusBg, borderRadius: BorderRadius.circular(10)),
                child: Text(status.replaceAll('_', ' '), style: TextStyle(color: statusColor, fontSize: 10, fontWeight: FontWeight.w900)),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text("$name • $purok", style: const TextStyle(fontSize: 13, color: Color(0xFF757575), fontWeight: FontWeight.w500)),
          const SizedBox(height: 16),
          Text(description, style: const TextStyle(fontSize: 14, color: Color(0xFF1A1A1A), height: 1.4, fontWeight: FontWeight.w500)),
          const SizedBox(height: 16),
          Row(
            children: [
              const Icon(Icons.access_time_rounded, size: 14, color: Color(0xFFBDBDBD)),
              const SizedBox(width: 6),
              Text(timestamp, style: const TextStyle(fontSize: 12, color: Color(0xFFBDBDBD), fontWeight: FontWeight.w500)),
              if (status == 'RESOLVED') ...[
                const SizedBox(width: 12),
                const Icon(Icons.check_circle_outline_rounded, size: 14, color: Color(0xFF4CAF50)),
                const SizedBox(width: 4),
                Text("Resolved", style: const TextStyle(fontSize: 12, color: Color(0xFF4CAF50), fontWeight: FontWeight.w600)),
              ],
            ],
          ),
          const SizedBox(height: 24),
          if (status != 'RESOLVED')
            Row(
              children: [
                if (status == 'PENDING') ...[
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () => _updateStatus(item['id'], 'IN PROGRESS', isResident),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFFE3F2FD),
                        foregroundColor: const Color(0xFF1976D2),
                        elevation: 0,
                        minimumSize: const Size(0, 52),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                      ),
                      child: const Text("Mark In Progress", style: TextStyle(fontWeight: FontWeight.w900)),
                    ),
                  ),
                  const SizedBox(width: 12),
                ],
                Expanded(
                  child: ElevatedButton(
                    onPressed: () => _showResolveDialog(item['id'], isResident),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFFE8F5E9),
                      foregroundColor: const Color(0xFF2E7D32),
                      elevation: 0,
                      minimumSize: const Size(0, 52),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                    ),
                    child: const Text("Resolve", style: TextStyle(fontWeight: FontWeight.w900)),
                  ),
                ),
              ],
            )
          else ...[
            // 🛡️ ADMIN RESPONSE
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: const Color(0xFFE8F5E9),
                borderRadius: BorderRadius.circular(16),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text("Admin Response:", style: TextStyle(color: Color(0xFF2E7D32), fontWeight: FontWeight.w900, fontSize: 13)),
                  const SizedBox(height: 4),
                  Text(item['admin_response'] ?? "Issue has been addressed and collection was completed.", style: const TextStyle(color: Color(0xFF2E7D32), fontSize: 13, fontWeight: FontWeight.w500)),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  Future<void> _updateStatus(dynamic id, String status, bool isResident, {String? adminResponse}) async {
    try {
      // Robust conversion for Database ENUM (e.g. "IN PROGRESS" -> "in_progress")
      String dbStatus = status.toLowerCase().trim().replaceAll(' ', '_');
      
      if (isResident) {
        final response = await _apiService.updateComplaint(
          int.parse(id.toString()),
          dbStatus,
          adminResponse,
        );
        if (response.data['success'] != true) {
          throw response.data['message'] ?? "Failed to update complaint";
        }
      } else {
        await _database.ref('notifications').child(id.toString()).update({
          'status': status,
          'admin_response': adminResponse,
          'resolved_at': status == 'RESOLVED' ? DateTime.now().toIso8601String() : null,
        });
      }
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text("Status updated to $status")));
        _fetchData();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text("Error: $e")));
      }
    }
  }

  void _showResolveDialog(dynamic id, bool isResident) {
    final TextEditingController responseController = TextEditingController();
    
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        title: const Text("Resolve Issue", style: TextStyle(fontWeight: FontWeight.w900)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text("Provide a resolution response for the user:"),
            const SizedBox(height: 16),
            TextField(
              controller: responseController,
              maxLines: 3,
              decoration: InputDecoration(
                hintText: "Enter resolution response...",
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text("Cancel")),
          ElevatedButton(
            onPressed: () {
              final response = responseController.text.trim();
              if (response.isEmpty) {
                ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("Please provide a response")));
                return;
              }
              _updateStatus(id, 'RESOLVED', isResident, adminResponse: response);
              Navigator.pop(context);
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF00BFA5),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
            child: const Text("Complete", style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }
}
