<?php
header("Content-Type: application/json");
require_once 'db_config.php';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $complaint_id = $_POST['complaint_id'] ?? null;
    $action = $_POST['action'] ?? null; // 'undo' or 'delete'

    if ($complaint_id && $action) {
        try {
            if ($action === 'undo') {
                // Hard delete: remove from database completely
                $query = "DELETE FROM complaints WHERE complaint_id = ?";
                $stmt = $conn->prepare($query);
                $stmt->execute([$complaint_id]);

                if ($stmt->rowCount() > 0) {
                    echo json_encode(["success" => true, "message" => "Complaint undone successfully"]);
                } else {
                    echo json_encode(["success" => false, "message" => "Complaint not found or already deleted"]);
                }
            } else if ($action === 'delete') {
                // Soft delete: hide from resident but keep for admin
                $query = "UPDATE complaints SET deleted_by_resident = 1 WHERE complaint_id = ?";
                $stmt = $conn->prepare($query);
                $stmt->execute([$complaint_id]);

                if ($stmt->rowCount() > 0) {
                    echo json_encode(["success" => true, "message" => "Complaint deleted successfully"]);
                } else {
                    echo json_encode(["success" => false, "message" => "Complaint not found or already deleted"]);
                }
            } else {
                echo json_encode(["success" => false, "message" => "Invalid action"]);
            }
        } catch (PDOException $e) {
            echo json_encode(["success" => false, "message" => "Database Error: " . $e->getMessage()]);
        }
    } else {
        echo json_encode(["success" => false, "message" => "Missing data"]);
    }
} else {
    echo json_encode(["success" => false, "message" => "Invalid request method"]);
}
?>
