<?php
header("Content-Type: application/json");
$backup_dir = 'backups/';

if (!isset($_GET['filename'])) {
    echo json_encode(["success" => false, "message" => "Filename not specified"]);
    exit;
}

$file = basename($_GET['filename']); // Security: prevent directory traversal
$filepath = $backup_dir . $file;

if (file_exists($filepath)) {
    if (unlink($filepath)) {
        echo json_encode(["success" => true, "message" => "Backup deleted successfully"]);
    } else {
        echo json_encode(["success" => false, "message" => "Failed to delete file"]);
    }
} else {
    echo json_encode(["success" => false, "message" => "File not found"]);
}
?>
