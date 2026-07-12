<?php
require_once 'db_config.php';

try {
    // Add archived_at column to residents table if not exists
    $conn->exec("ALTER TABLE residents ADD COLUMN IF NOT EXISTS archived_at DATETIME DEFAULT NULL");

    // Add archived_at column to users table if not exists
    $conn->exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS archived_at DATETIME DEFAULT NULL");

    echo "Database schema updated successfully.";
} catch (PDOException $e) {
    echo "Error updating database: " . $e->getMessage();
}
?>