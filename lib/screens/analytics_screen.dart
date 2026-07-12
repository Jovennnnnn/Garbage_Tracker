import 'package:flutter/material.dart';
import 'package:fl_chart/fl_chart.dart';
import 'package:firebase_database/firebase_database.dart';
import '../api/api_service.dart';
import '../utils/app_theme.dart';

class AnalyticsScreen extends StatefulWidget {
  final bool isEmbedded;
  final VoidCallback? onBack;
  const AnalyticsScreen({super.key, this.isEmbedded = false, this.onBack});

  @override
  State<AnalyticsScreen> createState() => _AnalyticsScreenState();
}

class _AnalyticsScreenState extends State<AnalyticsScreen> {
  final FirebaseDatabase _database = FirebaseDatabase.instance;
  final ApiService _apiService = ApiService();

  Map<String, double> _truckStatusData = {"Active": 1, "Full": 1, "Idle": 0};
  Map<String, double> _complaintStatusData = {"Pending": 2, "In Progress": 1, "Resolved": 0};
  String _selectedArea = "All Areas";

  @override
  void initState() {
    super.initState();
    _fetchChartData();
  }

  void _fetchChartData() {
    _database.ref('truck_locations').onValue.listen((event) {
      if (event.snapshot.exists) {
        final Map data = event.snapshot.value as Map;
        final Map<String, double> counts = {"Active": 0, "Idle": 0, "Full": 0};
        data.forEach((key, value) {
          String status = value['status']?.toString().toLowerCase() ?? 'idle';
          if (status == 'active') counts['Active'] = counts['Active']! + 1;
          else if (status == 'full') counts['Full'] = counts['Full']! + 1;
          else counts['Idle'] = counts['Idle']! + 1;
        });
        if (mounted) setState(() => _truckStatusData = counts);
      }
    });

    _apiService.getComplaints().then((response) {
      if (response.data['success'] == true) {
        final List complaints = response.data['data'];
        final Map<String, double> counts = {"Pending": 0, "In Progress": 0, "Resolved": 0};
        for (var c in complaints) {
          String status = c['status'].toString().toLowerCase().replaceAll('_', ' ');
          if (status == 'pending') counts['Pending'] = counts['Pending']! + 1;
          else if (status == 'in progress') counts['In Progress'] = counts['In Progress']! + 1;
          else if (status == 'resolved') counts['Resolved'] = counts['Resolved']! + 1;
        }
        if (mounted) setState(() => _complaintStatusData = counts);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(),
            _buildFilters(),
            Expanded(
              child: SingleChildScrollView(
                physics: const BouncingScrollPhysics(),
                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text("Viewing: $_selectedArea", style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 15, color: Color(0xFF1A1A1A))),
                    const SizedBox(height: 20),
                    Row(
                      children: [
                        Expanded(child: _buildSummaryCard("Routes Done", "0/12", "+0% vs last week", Icons.local_shipping_rounded, const Color(0xFFE8F5E9), const Color(0xFF2E7D32))),
                        const SizedBox(width: 16),
                        Expanded(child: _buildSummaryCard("Coverage", "0%", "Today's total", Icons.location_on_rounded, const Color(0xFFFFF8E1), const Color(0xFFFFA000))),
                      ],
                    ),
                    const SizedBox(height: 24),
                    _buildChartCard("Truck Status Distribution", _buildTruckDonutChart()),
                    const SizedBox(height: 24),
                    _buildChartCard("Complaints Status", _buildComplaintsDonutChart()),
                    const SizedBox(height: 24),
                    _buildChartCard("Purok Coverage (%)", _buildPurokBarChart(), hasEye: true, showViewDetails: true),
                    const SizedBox(height: 24),
                    _buildEfficiencyCard(),
                    const SizedBox(height: 32),
                    const Row(
                      children: [
                        Icon(Icons.auto_awesome_rounded, size: 22, color: Color(0xFF1A1A1A)),
                        SizedBox(width: 12),
                        Text("Predictions & Insights", style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
                      ],
                    ),
                    const SizedBox(height: 20),
                    _buildInsightCard("Waste Volume Prediction", [
                      _insightRow("Tomorrow:", "50667 kg", valueColor: const Color(0xFF1E88E5)),
                      _insightRow("This Week:", "312446 kg", valueColor: const Color(0xFF1E88E5)),
                      _insightRow("Truck Capacity:", "5000 kg", valueColor: const Color(0xFF1E88E5)),
                    ], const Color(0xFFE8EAF6), const Color(0xFF3F51B5)),
                    const SizedBox(height: 16),
                    _buildInsightCard("Estimated Arrival Times", [
                      _insightRow("Next Truck:", "Calculating live...", valueColor: const Color(0xFF4CAF50)),
                      _insightRow("Est. Time:", "N/A", valueColor: const Color(0xFF4CAF50)),
                    ], const Color(0xFFE8F5E9), const Color(0xFF2E7D32)),
                    const SizedBox(height: 16),
                    _buildInsightCard("Recommendations", [
                      const Text("• CRITICAL: Volume for Overall exceeds truck capacity. Overflow likely.", style: TextStyle(color: Color(0xFF9C27B0), fontSize: 13, fontWeight: FontWeight.w700, height: 1.4)),
                      const Text("• EVENT ALERT: Holiday detected. Expect heavy loads and slower collection.", style: TextStyle(color: Color(0xFF9C27B0), fontSize: 13, fontWeight: FontWeight.w700, height: 1.4)),
                      const Text("• Note: Volume adjusted for holidays & area size.", style: TextStyle(color: Color(0xFF9C27B0), fontSize: 13, fontWeight: FontWeight.w700, height: 1.4)),
                    ], const Color(0xFFF3E5F5), const Color(0xFF9C27B0)),
                    const SizedBox(height: 16),
                    _buildInsightCard("Performance Summary", [
                       _insightRow("Resolution Rate", "78.6%", valueColor: const Color(0xFF4CAF50)),
                       _insightRow("Avg Response Time", "0.0 hours"),
                       _insightRow("App Notifications", "0 sent"),
                       const Text("• Warning: 0 | • Entering: 0", style: TextStyle(color: Color(0xFF757575), fontSize: 12, fontWeight: FontWeight.w500)),
                    ], Colors.white, const Color(0xFF1A1A1A), showBorder: true),
                    const SizedBox(height: 40),
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
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text("Analytics & Reports", style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
                Text("System Performance", style: TextStyle(fontSize: 12, color: Color(0xFF757575), fontWeight: FontWeight.w500)),
              ],
            ),
          ),
          ElevatedButton.icon(
            onPressed: () => _showExportDialog(context),
            icon: const Icon(Icons.file_download_outlined, size: 20, color: Colors.white),
            label: const Text("Export", style: TextStyle(color: Colors.white, fontSize: 15, fontWeight: FontWeight.w900)),
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF00BFA5),
              elevation: 0,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFilters() {
    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(top: BorderSide(color: Color(0xFFF5F5F5))),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
      child: Row(
        children: [
          _filterItem(Icons.location_on_rounded, _selectedArea, onTap: () => _showAreaSelection(context)),
          const SizedBox(width: 12),
          Container(width: 1, height: 24, color: const Color(0xFFEEEEEE)),
          const SizedBox(width: 12),
          _filterItem(Icons.calendar_today_rounded, "Today", onTap: () => _showDatePicker(context)),
        ],
      ),
    );
  }

  Widget _filterItem(IconData icon, String label, {VoidCallback? onTap}) {
    return GestureDetector(
      onTap: onTap,
      child: Row(
        children: [
          Icon(icon, size: 18, color: const Color(0xFF2E7D32)),
          const SizedBox(width: 10),
          Text(label, style: const TextStyle(color: Color(0xFF2E7D32), fontSize: 15, fontWeight: FontWeight.w800)),
        ],
      ),
    );
  }

  Widget _buildSummaryCard(String title, String value, String trend, IconData icon, Color bgColor, Color iconColor) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(28),
        boxShadow: [BoxShadow(color: Colors.black.withAlpha(6), blurRadius: 10, offset: const Offset(0, 4))],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(color: Colors.white.withAlpha(150), borderRadius: BorderRadius.circular(12)),
                child: Icon(icon, color: iconColor, size: 22)
              ),
              const SizedBox(width: 12),
              Text(title, style: TextStyle(color: iconColor, fontSize: 12, fontWeight: FontWeight.w800)),
            ],
          ),
          const SizedBox(height: 24),
          Text(value, style: const TextStyle(color: Color(0xFF1A1A1A), fontSize: 26, fontWeight: FontWeight.w900)),
          const SizedBox(height: 2),
          Text(trend, style: TextStyle(color: iconColor, fontSize: 11, fontWeight: FontWeight.w900)),
        ],
      ),
    );
  }

  Widget _buildChartCard(String title, Widget chart, {bool hasEye = false, bool showViewDetails = false}) {
    return Container(
      padding: const EdgeInsets.all(28),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(32),
        boxShadow: [BoxShadow(color: Colors.black.withAlpha(6), blurRadius: 15, offset: const Offset(0, 8))]
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(title, style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 18, color: Color(0xFF1A1A1A))),
              if (hasEye) const Icon(Icons.visibility_rounded, color: Color(0xFF1E88E5), size: 22),
            ],
          ),
          const SizedBox(height: 32),
          SizedBox(height: title.contains("Purok") ? 450 : 260, child: chart),
          if (showViewDetails) ...[
            const SizedBox(height: 16),
            const Center(child: Text("Tap to view details", style: TextStyle(color: Color(0xFF1E88E5), fontSize: 12, fontWeight: FontWeight.w900))),
          ],
        ],
      ),
    );
  }

  Widget _buildTruckDonutChart() {
    List<PieChartSectionData> sections = [];
    _truckStatusData.forEach((key, value) {
      if (value > 0) {
        sections.add(PieChartSectionData(
          value: value,
          title: "${value.toInt()}\n$key",
          color: _getTruckColor(key),
          radius: 60,
          titleStyle: const TextStyle(color: Color(0xFF1A1A1A), fontWeight: FontWeight.w900, fontSize: 12),
          titlePositionPercentageOffset: 1.5,
        ));
      }
    });

    return Column(
      children: [
        Expanded(
          child: PieChart(
            PieChartData(
              sections: sections,
              centerSpaceRadius: 50,
              sectionsSpace: 0,
            ),
          ),
        ),
        const SizedBox(height: 20),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            _legendItem(const Color(0xFF4CAF50), "Active"),
            const SizedBox(width: 16),
            _legendItem(const Color(0xFFFFB300), "Full"),
            const SizedBox(width: 8),
            const Text("Truck Status", style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Color(0xFF757575))),
          ],
        ),
      ],
    );
  }

  Widget _buildComplaintsDonutChart() {
    List<PieChartSectionData> sections = [];
    _complaintStatusData.forEach((key, value) {
      if (value > 0) {
        sections.add(PieChartSectionData(
          value: value,
          title: "${value.toInt()}.00\n$key",
          color: _getComplaintColor(key),
          radius: 60,
          titleStyle: const TextStyle(color: Color(0xFF1A1A1A), fontWeight: FontWeight.w900, fontSize: 12),
          titlePositionPercentageOffset: 1.5,
        ));
      }
    });

    return Column(
      children: [
        Expanded(
          child: PieChart(
            PieChartData(
              sections: sections,
              centerSpaceRadius: 50,
              sectionsSpace: 0,
            ),
          ),
        ),
        const SizedBox(height: 20),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            _legendItem(const Color(0xFFFFB300), "Pending"),
            const SizedBox(width: 12),
            _legendItem(const Color(0xFF1E88E5), "In Progress"),
            const SizedBox(width: 8),
            const Text("Complaints Status", style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Color(0xFF757575))),
          ],
        ),
      ],
    );
  }

  Widget _buildPurokBarChart() {
    return BarChart(
      BarChartData(
        alignment: BarChartAlignment.spaceAround,
        maxY: 40,
        barTouchData: BarTouchData(enabled: false),
        titlesData: FlTitlesData(
          show: true,
          bottomTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          leftTitles: AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              reservedSize: 150,
              getTitlesWidget: (value, meta) {
                const areas = [
                  "Purok 2", "Purok 3", "Purok 4", "Dos Riles", "Sentro",
                  "San Isidro", "Paraiso", "Riverside", "Kalaw Street",
                  "Home Subdivision", "Tanco Road / Ayala Highway", "Brixton Area"
                ];
                if (value.toInt() >= 0 && value.toInt() < areas.length) {
                  return Text(areas[areas.length - 1 - value.toInt()], style: const TextStyle(fontSize: 10, color: Color(0xFF757575), fontWeight: FontWeight.w600));
                }
                return const Text("");
              },
            ),
          ),
          rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          topTitles: AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              getTitlesWidget: (value, meta) {
                if (value % 20 == 0) return Text("${value.toInt()}", style: const TextStyle(fontSize: 10, color: Color(0xFF757575)));
                return const Text("");
              },
            ),
          ),
        ),
        gridData: const FlGridData(show: true, drawVerticalLine: true, drawHorizontalLine: false),
        borderData: FlBorderData(show: false),
        barGroups: [
          _barGroup(0, 11), _barGroup(1, 0), _barGroup(2, 0), _barGroup(3, 10), _barGroup(4, 12),
          _barGroup(5, 0), _barGroup(6, 0), _barGroup(7, 0), _barGroup(8, 0), _barGroup(9, 0),
          _barGroup(10, 0), _barGroup(11, 0),
        ],
      ),
    );
  }

  BarChartGroupData _barGroup(int x, double y) {
    return BarChartGroupData(
      x: x,
      barRods: [
        BarChartRodData(toY: y, color: const Color(0xFF1E88E5), width: 12, borderRadius: BorderRadius.circular(4)),
      ],
    );
  }

  Widget _legendItem(Color color, String label) {
    return Row(
      children: [
        Container(width: 10, height: 10, decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(2))),
        const SizedBox(width: 6),
        Text(label, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: Color(0xFF757575))),
      ],
    );
  }

  Widget _buildEfficiencyCard() {
    return Container(
      padding: const EdgeInsets.all(28),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(32),
        boxShadow: [BoxShadow(color: Colors.black.withAlpha(6), blurRadius: 15, offset: const Offset(0, 8))]
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text("Route Efficiency", style: TextStyle(fontWeight: FontWeight.w900, fontSize: 18, color: Color(0xFF1A1A1A))),
          const SizedBox(height: 28),
          _efficiencyRow("Avg Collection Time", "0.0 hours"),
          _efficiencyRow("Stops per Route", "0 stops"),
          _efficiencyRow("Distance Covered", "0.0 km"),
          _efficiencyRow("Prediction Error (MAE)", "0.0s", valueColor: const Color(0xFF4CAF50)),
        ],
      ),
    );
  }

  Widget _efficiencyRow(String label, String value, {Color? valueColor}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 18),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(color: Color(0xFF1A1A1A), fontSize: 15, fontWeight: FontWeight.w600)),
          Text(value, style: TextStyle(fontWeight: FontWeight.w900, fontSize: 15, color: valueColor ?? const Color(0xFF1A1A1A))),
        ],
      ),
    );
  }

  Widget _buildInsightCard(String title, List<Widget> children, Color bgColor, Color titleColor, {bool showBorder = false}) {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(28),
        border: showBorder ? Border.all(color: const Color(0xFFF0F0F0)) : null,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: TextStyle(color: titleColor, fontWeight: FontWeight.w900, fontSize: 15)),
          const SizedBox(height: 16),
          ...children,
        ],
      ),
    );
  }

  Widget _insightRow(String label, String value, {Color? valueColor}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(fontSize: 14, color: Color(0xFF1A1A1A), fontWeight: FontWeight.w600)),
          Text(value, style: TextStyle(color: valueColor ?? const Color(0xFF1A1A1A), fontWeight: FontWeight.w900, fontSize: 14)),
        ],
      ),
    );
  }

  Color _getTruckColor(String key) {
    if (key == "Active") return const Color(0xFF4CAF50);
    if (key == "Full") return const Color(0xFFFFB300);
    return Colors.grey;
  }

  Color _getComplaintColor(String key) {
    if (key == "Pending") return const Color(0xFFFFB300);
    if (key == "In Progress") return const Color(0xFF1E88E5);
    return const Color(0xFF4CAF50);
  }

  // --- Modals ---

  void _showAreaSelection(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(32)),
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text("Global Filter: Select Area", style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900, color: Color(0xFF1A1A1A))),
              const SizedBox(height: 24),
              ConstrainedBox(
                constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.6),
                child: ListView(
                  shrinkWrap: true,
                  children: [
                    _areaItem("Purok 2"), _areaItem("Purok 3"), _areaItem("Purok 4"),
                    _areaItem("Dos Riles"), _areaItem("Sentro"), _areaItem("San Isidro"),
                    _areaItem("Paraiso"), _areaItem("Riverside"), _areaItem("Kalaw Street"),
                    _areaItem("Home Subdivision"), _areaItem("Tanco Road / Ayala Highway"), _areaItem("Brixton Area"),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              TextButton(
                onPressed: () {
                  setState(() => _selectedArea = "All Areas");
                  Navigator.pop(context);
                },
                child: const Text("Show All Areas", style: TextStyle(color: Color(0xFF1E88E5), fontWeight: FontWeight.w900)),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _areaItem(String name) {
    return ListTile(
      title: Text(name, style: const TextStyle(fontWeight: FontWeight.w800, color: Color(0xFF1A1A1A))),
      onTap: () {
        setState(() => _selectedArea = name);
        Navigator.pop(context);
      },
    );
  }

  void _showExportDialog(BuildContext context) {
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
                  const Icon(Icons.file_download_outlined, color: Color(0xFF00BFA5), size: 28),
                  const SizedBox(width: 12),
                  const Text("Export Reports", style: TextStyle(color: Color(0xFF00BFA5), fontSize: 22, fontWeight: FontWeight.w900)),
                ],
              ),
              const SizedBox(height: 8),
              const Align(
                alignment: Alignment.centerLeft,
                child: Text("Choose your export settings for system data", style: TextStyle(color: Color(0xFF757575), fontSize: 13, fontWeight: FontWeight.w500))
              ),
              const SizedBox(height: 32),
              _customDropdown("Report Type", ["All Reports", "Truck Performance", "Complaints Summary", "Route Efficiency", "Purok Coverage"]),
              const SizedBox(height: 20),
              _customDropdown("File Format", ["PDF Document (.pdf)", "Excel Spreadsheet (.xlsx)"]),
              const SizedBox(height: 20),
              Row(
                children: [
                  Expanded(child: _dateInput("Start Date", "2026-06-27")),
                  const SizedBox(width: 16),
                  Expanded(child: _dateInput("End Date", "2026-06-27")),
                ],
              ),
              const SizedBox(height: 40),
              ElevatedButton.icon(
                onPressed: () => Navigator.pop(context),
                icon: const Icon(Icons.file_download_outlined, color: Colors.white),
                label: const Text("Download Report", style: TextStyle(color: Colors.white, fontWeight: FontWeight.w900, fontSize: 16)),
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF00BFA5),
                  minimumSize: const Size(double.infinity, 60),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  elevation: 0,
                ),
              ),
              const SizedBox(height: 12),
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text("CANCEL", style: TextStyle(color: Color(0xFF757575), fontWeight: FontWeight.w900)),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _customDropdown(String hint, List<String> options) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 4),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE0E0E0)),
      ),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<String>(
          hint: Text(hint, style: const TextStyle(color: Color(0xFF757575), fontSize: 15, fontWeight: FontWeight.w500)),
          isExpanded: true,
          icon: const Icon(Icons.arrow_drop_down_rounded, color: Color(0xFF757575)),
          items: options.map((String value) {
            return DropdownMenuItem<String>(value: value, child: Text(value, style: const TextStyle(fontWeight: FontWeight.w500)));
          }).toList(),
          onChanged: (_) {},
        ),
      ),
    );
  }

  Widget _dateInput(String label, String value) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE0E0E0)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: const TextStyle(fontSize: 10, color: Color(0xFFBDBDBD), fontWeight: FontWeight.w900)),
          const SizedBox(height: 4),
          Text(value, style: const TextStyle(fontSize: 14, color: Color(0xFF1A1A1A), fontWeight: FontWeight.w900)),
        ],
      ),
    );
  }

  void _showDatePicker(BuildContext context) {
    showDatePicker(
      context: context,
      initialDate: DateTime.now(),
      firstDate: DateTime(2020),
      lastDate: DateTime(2030),
      builder: (context, child) {
        return Theme(
          data: Theme.of(context).copyWith(
            colorScheme: const ColorScheme.light(primary: Color(0xFF00BFA5)),
          ),
          child: child!,
        );
      },
    );
  }
}
