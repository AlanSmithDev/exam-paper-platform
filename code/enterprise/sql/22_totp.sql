-- =============================================================
-- 22_totp.sql —— 管理端 TOTP 双因子（docs/20 §6 等保二级"双因子管理端登录"）
-- 库：examforge_user（user 表增量）
-- =============================================================
USE examforge_user;

ALTER TABLE `user`
    ADD COLUMN `totp_secret`  VARCHAR(64) NULL COMMENT 'TOTP 密钥（Base32，setup 时写入）',
    ADD COLUMN `totp_enabled` TINYINT    NOT NULL DEFAULT 0 COMMENT '1=已启用（admin-login 强制校验）';
