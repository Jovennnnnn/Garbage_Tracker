<?php
include 'db_config.php';

// Create backups directory if it doesn't exist
$backup_dir = 'backups/';
if (!file_exists($backup_dir)) {
    mkdir($backup_dir, 0777, true);
}

$filename = 'backup_' . date('Y-m-d_H-i-s') . '.sql';
$filepath = $backup_dir . $filename;

/**
 * Custom function to generate a real SQL dump of the database
 * This is used when mysqldump is not available in the environment
 */
function generate_sql_dump($conn, $db_name) {
    $sql = "-- Garbage Sis Database Backup\n";
    $sql .= "-- Generated: " . date('Y-m-d H:i:s') . "\n";
    $sql .= "SET FOREIGN_KEY_CHECKS=0;\n\n";

    // Get all tables
    $tables = array();
    $result = $conn->query("SHOW TABLES");
    while ($row = $result->fetch(PDO::FETCH_NUM)) {
        $tables[] = $row[0];
    }

    foreach ($tables as $table) {
        // Drop and Create table structure
        $sql .= "DROP TABLE IF EXISTS `$table`;\n";
        $row2 = $conn->query("SHOW CREATE TABLE `$table`")->fetch(PDO::FETCH_NUM);
        $sql .= $row2[1] . ";\n\n";

        // Get table data
        $result = $conn->query("SELECT * FROM `$table`");
        while ($row = $result->fetch(PDO::FETCH_NUM)) {
            $sql .= "INSERT INTO `$table` VALUES(";
            $values = array();
            foreach ($row as $val) {
                if (isset($val)) {
                    $values[] = $conn->quote($val);
                } else {
                    $values[] = "NULL";
                }
            }
            $sql .= implode(",", $values);
            $sql .= ");\n";
        }
        $sql .= "\n\n";
    }

    $sql .= "SET FOREIGN_KEY_CHECKS=1;";
    return $sql;
}

try {
    $sql_content = generate_sql_dump($conn, $db_name);

    if (file_put_contents($filepath, $sql_content)) {
        echo json_encode([
            "success" => true,
            "message" => "Full database snapshot created successfully",
            "filename" => $filename,
            "size" => round(filesize($filepath) / 1024, 2) . " KB"
        ]);
    } else {
        throw new Exception("Failed to write to file");
    }
} catch (Exception $e) {
    echo json_encode([
        "success" => false,
        "message" => "Backup error: " . $e->getMessage()
    ]);
}
?>
