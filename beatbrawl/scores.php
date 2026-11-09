<?php
require __DIR__ . '/db.php';

try {
    $rows = db()->query(
        'SELECT player_name AS name, score, accuracy, max_combo AS combo, result
         FROM scores
         ORDER BY score DESC, accuracy DESC, id ASC
         LIMIT 10'
    )->fetchAll();
    json_out(['ok' => true, 'scores' => $rows]);
} catch (Throwable $e) {
    json_out(['ok' => false, 'error' => 'Database error'], 500);
}
