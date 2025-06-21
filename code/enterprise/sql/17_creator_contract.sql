-- ============================================================
-- ExamForge 17: 创作者签约合同（T-26f 收尾，docs/26 §6 第 3 条：主体/比例/结算周期）
-- 签约分成比例优先于全局默认（examforge.resource.share-pct）；结算周期当前仅 MONTHLY
-- ============================================================
USE examforge_resource;

CREATE TABLE IF NOT EXISTS `creator_contract` (
  `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
  `creator_user_id` BIGINT NOT NULL,
  `subject`         VARCHAR(128) NOT NULL COMMENT '签约主体（实名/笔名/机构）',
  `rate_pct`        INT NOT NULL COMMENT '签约分成比例%（覆盖全局默认）',
  `settle_cycle`    VARCHAR(16) NOT NULL DEFAULT 'MONTHLY' COMMENT '结算周期（当前仅 MONTHLY）',
  `status`          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE=生效中 ENDED=已结束',
  `start_at`        DATETIME NULL COMMENT '生效时间（NULL=立即）',
  `end_at`          DATETIME NULL COMMENT '结束时间（NULL=长期有效）',
  `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_creator` (`creator_user_id`, `status`)
) ENGINE=InnoDB COMMENT='创作者签约合同（docs/26 §6）';
