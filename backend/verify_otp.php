<?php
header("Content-Type: application/json");
date_default_timezone_set('Asia/Manila');
require_once 'db_config.php';

$data = json_decode(file_get_contents("php://input"));

if (!$data || empty($data->email) || empty($data->otp)) {
    echo json_encode(["success" => false, "message" => "Email and OTP are required"]);
    exit;
}

$email = $data->email;
$otp = $data->otp;

try {
    $now = date("Y-m-d H:i:s");
    $query = "SELECT * FROM password_resets WHERE email = ? AND token = ? AND expiry > ?";
    $stmt = $conn->prepare($query);
    $stmt->execute([$email, $otp, $now]);
    $reset = $stmt->fetch();

    if ($reset) {
        echo json_encode(["success" => true, "message" => "OTP verified successfully"]);
    } else {
        echo json_encode(["success" => false, "message" => "Invalid or expired OTP"]);
    }

} catch (PDOException $e) {
    echo json_encode(["success" => false, "message" => "Database Error: " . $e->getMessage()]);
}
?>