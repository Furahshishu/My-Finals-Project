-- Run this once in phpMyAdmin (Import tab, or paste into the SQL tab).
CREATE DATABASE IF NOT EXISTS beat_brawl
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE beat_brawl;

CREATE TABLE IF NOT EXISTS scores (
  id          INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  player_name VARCHAR(20)  NOT NULL,
  score       INT          NOT NULL,
  accuracy    TINYINT UNSIGNED NOT NULL,
  max_combo   SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  result      ENUM('win','lose') NOT NULL,
  played_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_score (score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
