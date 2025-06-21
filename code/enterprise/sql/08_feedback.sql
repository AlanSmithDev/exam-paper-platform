-- ============================================================
-- 智卷云 08: 纠错工单（docs/10 §2.2 纠错体系：分类+奖励+闭环）
-- ============================================================
USE examforge_question;

CREATE TABLE IF NOT EXISTS `feedback` (
  `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
  `question_id`  BIGINT NOT NULL,
  `user_id`      BIGINT NOT NULL,
  `target_type`  VARCHAR(20) NOT NULL COMMENT '题干错误/属性错误/解析知识性错误/解析细节错误/其他错误',
  `description`  VARCHAR(1000) NOT NULL,
  `images`       JSON COMMENT '截图证据（OSS URL 数组）',
  `status`       TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1已采纳 2已驳回',
  `reward_points` INT NOT NULL DEFAULT 0 COMMENT '采纳奖励点数',
  `handler`      VARCHAR(50) COMMENT '处理编辑',
  `resolve_remark` VARCHAR(500),
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_at`  DATETIME,
  KEY `idx_question` (`question_id`),
  KEY `idx_status` (`status`,`created_at`)
) ENGINE=InnoDB COMMENT='纠错工单';
