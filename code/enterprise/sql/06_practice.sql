-- ============================================================
-- 智卷云 06: 练习库 examforge_practice（docs/15 §1）
-- ============================================================
CREATE DATABASE IF NOT EXISTS examforge_practice DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE examforge_practice;

CREATE TABLE IF NOT EXISTS `practice` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`     BIGINT NOT NULL,
  `subject_id`  BIGINT,
  `mode`        VARCHAR(20) NOT NULL DEFAULT 'KP' COMMENT 'KP知识点/WRONG错题再练',
  `question_ids` JSON COMMENT '题目id数组',
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='练习';

CREATE TABLE IF NOT EXISTS `practice_answer` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `practice_id` BIGINT NOT NULL,
  `question_id` BIGINT NOT NULL,
  `answer`      VARCHAR(500),
  `correct`     TINYINT COMMENT '1对 0错 NULL待批改',
  `duration_ms` INT,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_practice` (`practice_id`)
) ENGINE=InnoDB COMMENT='作答记录';

CREATE TABLE IF NOT EXISTS `wrong_question` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`       BIGINT NOT NULL,
  `question_id`   BIGINT NOT NULL,
  `kp_names`      VARCHAR(300),
  `wrong_count`   INT NOT NULL DEFAULT 1,
  `resolved`      TINYINT NOT NULL DEFAULT 0,
  `last_wrong_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user_q` (`user_id`,`question_id`),
  KEY `idx_user_resolved` (`user_id`,`resolved`)
) ENGINE=InnoDB COMMENT='错题本';
