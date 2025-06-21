-- ============================================================
-- ExamForge 16: 创作者分成 P3 月度结算（T-26f，docs/26 §6：结算周期 + 失败补发）
-- 即时入账失败的账本行（credit_status=0）由月度结算任务按自然月聚合补发；
-- 历史行（本脚本前）按"已即时入账"处理（DEFAULT 1），避免结算重复补发。
-- ============================================================
USE examforge_resource;

-- 账本补入账状态与结算单关联
ALTER TABLE `creator_earning`
  ADD COLUMN `credit_status` TINYINT NOT NULL DEFAULT 1 COMMENT '入账状态：1已入账 0待结算补发' AFTER `rate_pct`,
  ADD COLUMN `settlement_id` BIGINT NULL COMMENT '结算单ID（补发入账后回填）' AFTER `credit_status`,
  ADD KEY `idx_credit_status` (`credit_status`, `created_at`);

-- 月度结算单（每次补发入账成功生成一单；同创作者同月可能多单=分批补发）
CREATE TABLE IF NOT EXISTS `creator_settlement` (
  `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
  `creator_user_id` BIGINT NOT NULL,
  `month`           CHAR(7) NOT NULL COMMENT '结算月 yyyy-MM（按账本 created_at 自然月）',
  `share_cents`     INT NOT NULL COMMENT '本单补发分成点数合计',
  `row_count`       INT NOT NULL COMMENT '本单覆盖账本行数',
  `status`          TINYINT NOT NULL DEFAULT 1 COMMENT '1=已入账',
  `credited_at`     DATETIME NOT NULL COMMENT '入账时间',
  `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_creator` (`creator_user_id`, `created_at`),
  KEY `idx_month` (`month`)
) ENGINE=InnoDB COMMENT='创作者分成月度结算单（P3 补发，docs/26 §6）';
