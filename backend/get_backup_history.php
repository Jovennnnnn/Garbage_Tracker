<?php
$backup_dir = 'backups/';
$backups = [];

if (file_exists($backup_dir)) {
    $files = scandir($backup_dir, SCANDIR_SORT_DESCENDING);
    foreach ($files as $file) {
        if ($file !== '.' && $file !== '..' && strpos($file, '.sql') !== false) {
            $filepath = $backup_dir . $file;
            $backups[] = [
                "filename" => $file,
                "size" => round(filesize($filepath) / 1024, 2) . " KB",
                "date" => date("Y-m-d H:i:s", filemtime($filepath)),
                "url" => "http://" . $_SERVER['HTTP_HOST'] . dirname($_SERVER['PHP_SELF']) . "/" . $filepath
            ];
        }
    }
}

echo json_encode([
    "success" => true,
    "backups" => $backups
]);
?>
