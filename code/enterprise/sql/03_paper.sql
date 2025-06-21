-- ============================================================
-- 智卷云 03: 组卷库 examforge_paper
-- ============================================================
CREATE DATABASE IF NOT EXISTS examforge_paper DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE examforge_paper;

CREATE TABLE IF NOT EXISTS `basket` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`     BIGINT NOT NULL,
  `question_id` BIGINT NOT NULL,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user_q` (`user_id`,`question_id`)
) ENGINE=InnoDB COMMENT='试题篮（上限100）';

CREATE TABLE IF NOT EXISTS `paper` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`     BIGINT NOT NULL,
  `title`       VARCHAR(200) NOT NULL DEFAULT '智能组卷',
  `blueprint`   JSON COMMENT '蓝图：structure/difficultyTarget/kpCoverage',
  `total_score` INT,
  `status`      TINYINT NOT NULL DEFAULT 0 COMMENT '0编辑 1定稿',
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='试卷';

CREATE TABLE IF NOT EXISTS `paper_question` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `paper_id`    BIGINT NOT NULL,
  `question_id` BIGINT NOT NULL,
  `sort`        INT NOT NULL,
  `score`       INT NOT NULL,
  `snapshot`    JSON COMMENT '定稿内容快照',
  UNIQUE KEY `uk_paper_q` (`paper_id`,`question_id`),
  KEY `idx_paper` (`paper_id`,`sort`)
) ENGINE=InnoDB COMMENT='试卷题目';
