-- ============================================================
-- 智卷云 07: AI 库 examforge_ai（docs/15 §2 AI-4 治理）
-- ============================================================
CREATE DATABASE IF NOT EXISTS examforge_ai DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE examforge_ai;

CREATE TABLE IF NOT EXISTS `ai_log` (
  `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`      BIGINT,
  `scene`        VARCHAR(30) NOT NULL COMMENT 'VARIANT出题/EXPLAIN讲题',
  `provider`     VARCHAR(30) NOT NULL,
  `model`        VARCHAR(60),
  `prompt_chars` INT,
  `resp_chars`   INT,
  `cost_ms`      INT,
  `degraded`     TINYINT NOT NULL DEFAULT 0,
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_user` (`user_id`,`created_at`)
) ENGINE=InnoDB COMMENT='AI 调用审计（成本看板数据源）';
