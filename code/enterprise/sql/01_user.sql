-- ============================================================
-- 智卷云 01: 用户库 examforge_user（RBAC，见 docs/12）
-- ============================================================
CREATE DATABASE IF NOT EXISTS examforge_user DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE examforge_user;

CREATE TABLE IF NOT EXISTS `user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `mobile`        VARCHAR(20)  NOT NULL COMMENT '手机号（生产环境：AES 加密存 varbinary）',
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'bcrypt',
  `nickname`      VARCHAR(50),
  `role`          VARCHAR(20)  NOT NULL DEFAULT 'TEACHER' COMMENT 'SUPER_ADMIN/OP/EDITOR/TEACHER/STUDENT',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0封禁',
  `certify`       TINYINT      NOT NULL DEFAULT 0 COMMENT '教师认证',
  `member_until`  DATE,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mobile` (`mobile`),
  KEY `idx_role` (`role`)
) ENGINE=InnoDB COMMENT='用户';

-- 种子：超管（密码 Admin@123456 的 bcrypt 哈希，上线后必须修改）
INSERT INTO `user`(`mobile`,`password_hash`,`nickname`,`role`,`certify`)
VALUES ('13000000000','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','超级管理员','SUPER_ADMIN',1)
ON DUPLICATE KEY UPDATE nickname=VALUES(nickname);
