<?php
/**
 * This script handles automatic daily backups.
 */
include_once 'db_config.php';

function check_and_perform_auto_backup($conn) {
    $backup_dir = 'backups/';
    if (!file_exists($backup_dir)) {
        mkdir($backup_dir, 0777, true);
    }

    $last_backup_file = $backup_dir . 'last_auto_backup.txt';
    $today = date('Y-m-d');

    // Check if we already backed up today
    if (file_exists($last_backup_file)) {
        $last_date = trim(file_get_contents($last_backup_file));
        if ($last_date === $today) {
            return; // Already done for today
        }
    }

    // Perform the backup
    // Logic extracted from trigger_backup.php to be reusable
    $filename = 'auto_backup_' . date('Y-m-d_H-i-s') . '.sql';
    $filepath = $backup_dir . $filename;

    if (!function_exists('generate_sql_dump')) {
        function generate_sql_dump($conn) {
            $sql = "-- Garbage Sis Automatic Daily Backup\n";
            $sql .= "-- Generated: " . date('Y-m-d H:i:s') . "\n";
            $sql .= "SET FOREIGN_KEY_CHECKS=0;\n\n";

            $tables = array();
            $result = $conn->query("SHOW TABLES");
            while ($row = $result->fetch(PDO::FETCH_NUM)) {
                $tables[] = $row[0];
            }

            foreach ($tables as $table) {
                $sql .= "DROP TABLE IF EXISTS `$table`;\n";
                $row2 = $conn->query("SHOW CREATE TABLE `$table`")->fetch(PDO::FETCH_NUM);
                $sql .= $row2[1] . ";\n\n";

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
    }

    try {
        $sql_content = generate_sql_dump($conn);
        if (file_put_contents($filepath, $sql_content)) {
            file_put_contents($last_backup_file, $today);
        }
    } catch (Exception $e) {
        // Silently fail or log for auto-backup
    }
}
?>
