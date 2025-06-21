-- =============================================================
-- 21_login_guard.sql —— 登录防爆破流水（docs/20 §6 等保二级"登录防爆破"；
-- DB 流水口径：每次尝试留痕满足等保审计，锁定判定为滑动窗口）
-- 库：examforge_user
-- =============================================================
USE examforge_user;

CREATE TABLE IF NOT EXISTS `login_attempt` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `mobile`     VARCHAR(20) NOT NULL,
  `ip`         VARCHAR(45) NOT NULL DEFAULT '' COMMENT 'IPv4/IPv6（网关 X-Forwarded-For）',
  `success`    TINYINT     NOT NULL,
  `created_at` DATETIME    NOT NULL,
  KEY idx_mobile_time (mobile, created_at),
  KEY idx_ip_time (ip, created_at)
) ENGINE=InnoDB COMMENT='登录尝试流水（防爆破 + 等保审计）';
