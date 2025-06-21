-- =============================================================
-- 19_payment_d14.sql —— 支付渠道 D14：回调幂等三板斧 R7 第②板（防重放唯一索引）
-- 库：examforge_trade（payment_callback 表已存在于 05_trade.sql，本脚本增量演进）
-- =============================================================
USE examforge_trade;

-- 渠道回调唯一标识（微信：通知 ID；支付宝：notify_id；MOCK：orderNo+时间戳生成）
ALTER TABLE `payment_callback`
    ADD COLUMN `callback_id` VARCHAR(64) NULL AFTER `provider`,
    ADD COLUMN `paid` TINYINT NULL AFTER `callback_id`;

-- 防重放：同一渠道同一回调 ID 只处理一次（历史行 callback_id 为 NULL，MySQL 唯一索引允许多 NULL，向后兼容）
CREATE UNIQUE INDEX uk_provider_callback ON `payment_callback` (`provider`, `callback_id`);
