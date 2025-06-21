-- ============================================================
-- ExamForge 15: 创作者分成首期（T-26f，docs/26 §6：上传→上架→下载计费→分成入账）
-- 分成比例运营可配（examforge.resource.share-pct，默认 50%）；点数即时入账 + 账本留痕（月度结算提现为 P3）
-- ============================================================
USE examforge_resource;

-- 资源表补充创作者（上传人用户ID；创作者上传 status=0 进既有 admin 上架审核流）
ALTER TABLE `resource_item`
  ADD COLUMN `creator_user_id` BIGINT NULL COMMENT '创作者用户ID（分成收益归属）' AFTER `author`;

-- 分成账本（下载计费成功即记账；自下载不分成）
CREATE TABLE IF NOT EXISTS `creator_earning` (
  `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
  `creator_user_id` BIGINT NOT NULL,
  `resource_id`     BIGINT NOT NULL,
  `downloader_id`   BIGINT NOT NULL,
  `amount_cents`    INT NOT NULL COMMENT '下载实收点数（1点=1分等值）',
  `share_cents`     INT NOT NULL COMMENT '创作者分成点数',
  `rate_pct`        INT NOT NULL COMMENT '分成比例%（记账时点快照）',
  `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_creator` (`creator_user_id`, `created_at`),
  KEY `idx_resource` (`resource_id`)
) ENGINE=InnoDB COMMENT='创作者分成账本（月度结算提现为 P3）';
