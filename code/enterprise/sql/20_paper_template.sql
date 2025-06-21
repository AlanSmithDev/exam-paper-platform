-- =============================================================
-- 20_paper_template.sql —— 试卷模板（docs/25 TJ-80/TJ-22"存为模版/模板选题"）
-- 库：examforge_paper
-- =============================================================
USE examforge_paper;

CREATE TABLE IF NOT EXISTS `paper_template` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`    BIGINT       NOT NULL COMMENT '归属教师',
  `name`       VARCHAR(64)  NOT NULL COMMENT '模板名',
  `source_paper_id` BIGINT  NULL COMMENT '来源试卷（可空=手工创建）',
  `questions`  JSON         NOT NULL COMMENT '题目快照 [{questionId,score}]（按卷面顺序）',
  `total_score` INT         NOT NULL DEFAULT 0,
  `created_at` DATETIME     NOT NULL,
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='试卷模板（题目快照）';
