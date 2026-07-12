<?php
header("Content-Type: application/json");
require_once 'db_config.php';
require_once 'auto_backup_checker.php';

// Trigger auto-backup check on login
check_and_perform_auto_backup($conn);

$data = json_decode(file_get_contents("php://input"));

if (!$data) {
    echo json_encode(["success" => false, "message" => "No data received"]);
    exit;
}

if (!empty($data->username_or_email) && !empty($data->password)) {
    try {
        $username_or_email = $data->username_or_email;

        // 1. Search sa 'users' table (Admin/Driver)
        $query = "SELECT user_id, username, name, email, phone, license_number, preferred_truck, role, password_hash, is_archived FROM users WHERE username = ? OR email = ?";
        $stmt = $conn->prepare($query);
        $stmt->execute([$username_or_email, $username_or_email]);
        $user = $stmt->fetch(PDO::FETCH_ASSOC);

        if ($user && password_verify($data->password, $user['password_hash'])) {
            if ($user['is_archived'] == 1) {
                $msg = ($user['role'] === 'driver')
                    ? "Your driver account is pending approval. Please wait for an administrator to activate it."
                    : "Your account has been archived. Please contact the administrator.";
                echo json_encode(["success" => false, "message" => $msg]);
                exit;
            }
            unset($user['password_hash']);
            unset($user['is_archived']);
            echo json_encode([
                "success" => true,
                "message" => "Login successful",
                "user" => $user
            ]);
            exit;
        }

        // 2. Search sa 'residents' table (Resident)
        $query = "SELECT resident_id as user_id, username, name, email, phone, purok, complete_address, 'resident' as role, password_hash, is_archived FROM residents WHERE username = ? OR email = ?";
        $stmt = $conn->prepare($query);
        $stmt->execute([$username_or_email, $username_or_email]);
        $resident = $stmt->fetch(PDO::FETCH_ASSOC);

        if ($resident && password_verify($data->password, $resident['password_hash'])) {
            if ($resident['is_archived'] == 1) {
                echo json_encode(["success" => false, "message" => "Your account is pending approval. Please wait for an administrator to verify your residency."]);
                exit;
            }
            unset($resident['password_hash']);
            unset($resident['is_archived']);
            echo json_encode([
                "success" => true,
                "message" => "Login successful",
                "user" => $resident
            ]);
            exit;
        }

        echo json_encode(["success" => false, "message" => "Invalid username/email or password"]);

    } catch (PDOException $e) {
        echo json_encode(["success" => false, "message" => "Database Error: " . $e->getMessage()]);
    }
} else {
    echo json_encode(["success" => false, "message" => "Incomplete data"]);
}
?>