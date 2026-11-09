<?php
require __DIR__ . '/db.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    json_out(['ok' => false, 'error' => 'POST only'], 405);
}

$in = json_decode(file_get_contents('php://input'), true);
if (!is_array($in)) {
    json_out(['ok' => false, 'error' => 'Invalid JSON'], 400);
}

$name = trim((string)($in['name'] ?? ''));
$name = preg_replace('/[\x00-\x1F\x7F]/u', '', $name);
$name = mb_substr($name, 0, 20);
if ($name === '') {
    $name = 'Player';
}

$score    = (int)($in['score'] ?? 0);
$accuracy = (int)($in['accuracy'] ?? 0);
$combo    = (int)($in['combo'] ?? 0);
$result   = ($in['result'] ?? '') === 'win' ? 'win' : 'lose';

// Basic sanity limits
$score    = max(-100000, min(1000000, $score));
$accuracy = max(0, min(100, $accuracy));
$combo    = max(0, min(5000, $combo));

try {
    $stmt = db()->prepare(
        'INSERT INTO scores (player_name, score, accuracy, max_combo, result)
         VALUES (:n, :s, :a, :c, :r)'
    );
    $stmt->execute([':n' => $name, ':s' => $score, ':a' => $accuracy, ':c' => $combo, ':r' => $result]);
    json_out(['ok' => true]);
} catch (Throwable $e) {
    json_out(['ok' => false, 'error' => 'Database error'], 500);
}
