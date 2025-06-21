-- =============================================================
-- 18_school.sql —— 学校订阅域（T-26h，docs/26 §7 网校通/学校服务线；docs/14 M-5）
-- 库：examforge_school ｜ 服务：examforge-school(8109)
-- =============================================================
CREATE DATABASE IF NOT EXISTS examforge_school DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE examforge_school;

-- 学校（B 端合同开通：超管人工开通，校管理员=admin_user_id）
CREATE TABLE IF NOT EXISTS school (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(128) NOT NULL COMMENT '学校名称',
  admin_user_id BIGINT       NOT NULL COMMENT '校管理员用户ID（开通时指定）',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=OPEN 0=CLOSED',
  seat_limit    INT          NULL COMMENT '教师席位上限（NULL=不限）',
  member_until  DATETIME     NULL COMMENT '订阅到期时间',
  created_at    DATETIME     NOT NULL,
  UNIQUE KEY uk_admin (admin_user_id),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='学校订阅';

-- 学校成员（教师享会员权益的口径载体）
CREATE TABLE IF NOT EXISTS school_member (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  school_id  BIGINT      NOT NULL,
  user_id    BIGINT      NOT NULL,
  role       VARCHAR(16) NOT NULL DEFAULT 'TEACHER' COMMENT 'TEACHER|STUDENT',
  joined_at  DATETIME    NOT NULL,
  UNIQUE KEY uk_school_user (school_id, user_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='学校成员';
