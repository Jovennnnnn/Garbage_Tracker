<?php
require_once 'db_config.php';
error_reporting(E_ALL);
ini_set('display_errors', 0);

$type = $_GET['type'] ?? 'All Reports';
$format = strtolower($_GET['format'] ?? 'xls');
$start = $_GET['start_date'] ?? date('Y-m-d');
$end = $_GET['end_date'] ?? date('Y-m-d');

// ✅ SYNCED DATA FROM APP
$res_rate = $_GET['res_rate'] ?? '0.0%';
$avg_time = $_GET['avg_time'] ?? '0.0 hours';
$tomorrow = $_GET['tomorrow'] ?? '0 kg';
$weekly = $_GET['weekly'] ?? '0 kg';
$coverage = $_GET['coverage'] ?? '0%';
$routes_done = $_GET['routes_done'] ?? '0/12';

// Fleet Distribution
$active_count = $_GET['active_count'] ?? '0';
$collecting_count = $_GET['collecting_count'] ?? '0';
$full_count = $_GET['full_count'] ?? '0';
$inactive_count = $_GET['inactive_count'] ?? '0';

// Complaints Summary
$pending_count = $_GET['pending_count'] ?? '0';
$inprogress_count = $_GET['inprogress_count'] ?? '0';
$resolved_count = $_GET['resolved_count'] ?? '0';

// Efficiency
$dist = $_GET['dist'] ?? '0.0 km';
$stops = $_GET['stops'] ?? '0 stops';
$coll_time = $_GET['coll_time'] ?? '0.0 hours';
$error = $_GET['error'] ?? '0.0s';

// Insights
$insight1 = $_GET['insight1'] ?? '';
$insight2 = $_GET['insight2'] ?? '';

$is_xls = ($format === 'xls' || $format === 'xlsx');

if ($is_xls) {
    header("Content-Type: application/vnd.ms-excel");
    header("Content-Disposition: attachment; filename=\"Official_Report_" . date("Ymd_His") . ".xls\"");
    render_excel($conn, $type, $start, $end, $res_rate, $avg_time, $tomorrow, $weekly, $coverage, $routes_done, $dist, $stops, $coll_time, $error, $insight1, $insight2, $active_count, $collecting_count, $full_count, $inactive_count, $pending_count, $inprogress_count, $resolved_count);
} else {
    // Basic CSV fallback (simplified)
    header("Content-Type: text/csv");
    header("Content-Disposition: attachment; filename=\"Report_" . date("Ymd_His") . ".csv\"");
    $out = fopen("php://output", "w");
    fprintf($out, chr(0xEF).chr(0xBB).chr(0xBF));
    fputcsv($out, ["GARBAGE TRACKING SYSTEM OFFICIAL REPORT"]);
    fputcsv($out, ["Period:", $start, "to", $end]);
    fclose($out);
}
exit;

function render_excel($conn, $type, $start, $end, $res_rate, $avg_time, $tomorrow, $weekly, $coverage, $routes_done, $dist, $stops, $coll_time, $error, $insight1, $insight2, $active_count, $collecting_count, $full_count, $inactive_count, $pending_count, $inprogress_count, $resolved_count) { ?>
    <html><head><meta charset="utf-8">
    <style>
        /* High-End Professional Design Styles */
        .report-header {
            background: #004D40;
            color: #FFFFFF;
            font-size: 20pt;
            font-weight: bold;
            text-align: center;
            padding: 15px;
            font-family: 'Segoe UI', Arial, sans-serif;
            border: 1pt solid #00251A;
        }
        .report-sub-title {
            background: #00897B;
            color: #FFFFFF;
            font-size: 13pt;
            font-weight: bold;
            text-align: center;
            padding: 8px;
            border-bottom: 1pt solid #004D40;
        }
        .meta-label {
            background: #E8F5E9;
            color: #1B5E20;
            font-weight: bold;
            font-size: 10pt;
            text-align: left;
            padding: 5px;
            border: 0.5pt solid #C8E6C9;
        }
        .meta-value {
            color: #333333;
            font-size: 10pt;
            text-align: left;
            padding: 5px;
            border: 0.5pt solid #E0E0E0;
        }
        .section-header-box {
            background: #00BFA5;
            color: #FFFFFF;
            font-weight: bold;
            font-size: 14pt;
            text-align: left;
            padding: 10px;
            border: 1pt solid #00897B;
        }
        .column-header-box {
            background: #E0F2F1;
            color: #004D40;
            font-weight: bold;
            text-align: center;
            border: 0.5pt solid #B2DFDB;
            padding: 8px;
            font-size: 10pt;
        }
        .summary-header {
            background: #FFFFFF;
            color: #00695C;
            font-weight: bold;
            text-align: center;
            border: 0.5pt solid #B2DFDB;
            padding: 6px;
            font-size: 9.5pt;
        }
        .data-cell {
            border: 0.5pt solid #E0E0E0;
            text-align: left;
            padding: 8px;
            color: #212121;
            font-size: 9.5pt;
            background: #ffffff;
        }
        .data-cell-center {
            border: 0.5pt solid #E0E0E0;
            text-align: center;
            padding: 8px;
            color: #212121;
            font-size: 9.5pt;
        }
        .no-data-msg {
            color: #D32F2F;
            font-style: italic;
            text-align: center;
            border: 0.5pt solid #E0E0E0;
            padding: 12px;
            background: #FFEBEE;
        }
        .spacer { height: 25px; }
        .highlight-bold { font-weight: bold; color: #00796B; }
    </style></head><body>
    <table>
        <!-- MAIN HEADER -->
        <tr><td colspan="12" class="report-header">GARBAGE TRACKING & ANALYTICS REPORT</td></tr>
        <tr><td colspan="12" class="report-sub-title">Official Intelligence Summary & Operational Insights</td></tr>

        <!-- META INFO -->
        <tr>
            <td colspan="4" class="meta-label">Period Selected:</td>
            <td colspan="8" class="meta-value"><?php echo $start; ?> to <?php echo $end; ?></td>
        </tr>
        <tr>
            <td colspan="4" class="meta-label">Date Generated:</td>
            <td colspan="8" class="meta-value"><?php echo date('F d, Y - h:i A'); ?></td>
        </tr>
        <tr>
            <td colspan="4" class="meta-label">Report Type:</td>
            <td colspan="8" class="meta-value"><?php echo strtoupper($type); ?></td>
        </tr>

        <tr><td colspan="12" class="spacer"></td></tr>

        <?php
        $secs = ($type == 'All Reports') ? ['Performance Overview', 'Waste Predictions', 'Truck & Fleet Status', 'Complaints Analytics', 'Operational Efficiency', 'Purok Coverage Details'] : [$type];

        foreach ($secs as $s) {
            // Section Title
            echo "<tr><td colspan='12' class='section-header-box'>$s</td></tr>";

            switch($s) {
                case 'Performance Overview':
                    echo "<tr>
                        <td class='column-header-box' colspan='3'>Resolution Rate</td>
                        <td class='column-header-box' colspan='3'>Avg Response Time</td>
                        <td class='column-header-box' colspan='3'>Purok Coverage</td>
                        <td class='column-header-box' colspan='3'>Routes Completed</td>
                    </tr>";
                    echo "<tr>
                        <td class='data-cell-center' colspan='3'>$res_rate</td>
                        <td class='data-cell-center' colspan='3'>$avg_time</td>
                        <td class='data-cell-center' colspan='3'>$coverage</td>
                        <td class='data-cell-center' colspan='3'>$routes_done</td>
                    </tr>";
                    break;

                case 'Waste Predictions':
                    echo "<tr>
                        <td class='column-header-box' colspan='3'>Tomorrow's Forecast</td>
                        <td class='column-header-box' colspan='3'>Weekly Forecast</td>
                        <td class='column-header-box' colspan='6'>AI Insights & Recommendations</td>
                    </tr>";
                    echo "<tr>
                        <td class='data-cell-center highlight-bold' colspan='3'>$tomorrow</td>
                        <td class='data-cell-center' colspan='3'>$weekly</td>
                        <td class='data-cell' colspan='6' style='line-height:1.5;'>$insight1 <br> $insight2</td>
                    </tr>";
                    break;

                case 'Truck & Fleet Status':
                    echo "<tr><td class='summary-header' colspan='12'>Fleet Distribution</td></tr>";
                    echo "<tr>
                        <td class='column-header-box' colspan='3'>Active: $active_count</td>
                        <td class='column-header-box' colspan='3'>Collecting: $collecting_count</td>
                        <td class='column-header-box' colspan='3'>Full: $full_count</td>
                        <td class='column-header-box' colspan='3'>Inactive: $inactive_count</td>
                    </tr>";
                    echo "<tr>
                        <td class='column-header-box'>ID</td>
                        <td class='column-header-box' colspan='3'>Plate Number</td>
                        <td class='column-header-box' colspan='5'>Driver Assigned</td>
                        <td class='column-header-box' colspan='3'>Current Status</td>
                    </tr>";

                    $stmt = $conn->query("SELECT t.truck_id, t.plate_number, u.name as driver_name, t.status FROM trucks t LEFT JOIN users u ON t.driver_id = u.user_id");
                    $c=0;
                    while($r = $stmt->fetch(PDO::FETCH_ASSOC)){
                        $c++;
                        echo "<tr>
                            <td class='data-cell-center'>#{$r['truck_id']}</td>
                            <td class='data-cell-center' colspan='3'>{$r['plate_number']}</td>
                            <td class='data-cell' colspan='5'>".($r['driver_name'] ?: 'N/A')."</td>
                            <td class='data-cell-center' style='font-weight:bold; color:".($r['status']=='active'?'#00796B':'#D32F2F').";' colspan='3'>".strtoupper($r['status'])."</td>
                        </tr>";
                    }
                    if(!$c) echo "<tr><td colspan='12' class='no-data-msg'>No registered trucks found in the system database.</td></tr>";
                    break;

                case 'Complaints Analytics':
                    echo "<tr><td class='summary-header' colspan='12'>Complaints Volume Summary</td></tr>";
                    echo "<tr>
                        <td class='column-header-box' colspan='4'>Pending: $pending_count</td>
                        <td class='column-header-box' colspan='4'>In Progress: $inprogress_count</td>
                        <td class='column-header-box' colspan='4'>Resolved: $resolved_count</td>
                    </tr>";
                    echo "<tr>
                        <td class='column-header-box'>ID</td>
                        <td class='column-header-box' colspan='2'>Category</td>
                        <td class='column-header-box' colspan='5'>Complaint Description</td>
                        <td class='column-header-box'>Status</td>
                        <td class='column-header-box' colspan='3'>Date Filed</td>
                    </tr>";

                    $stmt = $conn->prepare("SELECT complaint_id, category, description, status, created_at FROM complaints WHERE DATE(created_at) BETWEEN ? AND ? ORDER BY created_at DESC");
                    $stmt->execute([$start, $end]); $c=0;
                    while($r = $stmt->fetch(PDO::FETCH_ASSOC)){
                        $c++;
                        echo "<tr>
                            <td class='data-cell-center'>#{$r['complaint_id']}</td>
                            <td class='data-cell' colspan='2'>{$r['category']}</td>
                            <td class='data-cell' colspan='5'>{$r['description']}</td>
                            <td class='data-cell-center' style='font-weight:bold;'>".strtoupper($r['status'])."</td>
                            <td class='data-cell-center' colspan='3'>{$r['created_at']}</td>
                        </tr>";
                    }
                    if(!$c) echo "<tr><td colspan='12' class='no-data-msg'>No complaints found for the selected period.</td></tr>";
                    break;

                case 'Operational Efficiency':
                    echo "<tr>
                        <td class='column-header-box' colspan='3'>Total Distance</td>
                        <td class='column-header-box' colspan='3'>Total Stops</td>
                        <td class='column-header-box' colspan='3'>Avg Coll. Time</td>
                        <td class='column-header-box' colspan='3'>Prediction Error</td>
                    </tr>";
                    echo "<tr>
                        <td class='data-cell-center' colspan='3'>$dist</td>
                        <td class='data-cell-center' colspan='3'>$stops</td>
                        <td class='data-cell-center' colspan='3'>$coll_time</td>
                        <td class='data-cell-center' colspan='3'>$error</td>
                    </tr>";
                    break;

                case 'Purok Coverage Details':
                    echo "<tr>
                        <td class='column-header-box' colspan='5'>Purok Area Name</td>
                        <td class='column-header-box' colspan='3'>Visit Frequency</td>
                        <td class='column-header-box' colspan='4'>Last Collection Timestamp</td>
                    </tr>";
                    $stmt = $conn->prepare("SELECT a.area_name, COUNT(cl.log_id) as visits, MAX(cl.timestamp) as last_ts
                                          FROM areas a
                                          LEFT JOIN collection_logs cl ON a.area_id = cl.area_id AND DATE(cl.timestamp) BETWEEN ? AND ?
                                          GROUP BY a.area_id");
                    $stmt->execute([$start, $end]); $c=0;
                    while($r = $stmt->fetch(PDO::FETCH_ASSOC)){
                        $c++;
                        echo "<tr>
                            <td class='data-cell' colspan='5'>{$r['area_name']}</td>
                            <td class='data-cell-center' colspan='3'>{$r['visits']} visits</td>
                            <td class='data-cell-center' colspan='4'>".($r['last_ts'] ?: 'NO VISITS RECORDED')."</td>
                        </tr>";
                    }
                    if(!$c) echo "<tr><td colspan='12' class='no-data-msg'>No area data found.</td></tr>";
                    break;
            }

            // Interval spacing (10 rows)
            for($i=0; $i<10; $i++) echo "<tr><td colspan='12' style='height:25px;'></td></tr>";
        } ?>

        <tr><td colspan="12" style="text-align:center; color:#757575; font-size:9pt; padding-top:30px; border-top:1pt solid #EEEEEE;">
            © 2026 GarbageSis Tracking System | Official Analytics Report | Secure Data Export
        </td></tr>
    </table></body></html>
<?php } ?>
