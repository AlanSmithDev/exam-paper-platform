-- ============================================================
-- 智卷云 04: 管理库 examforge_admin（广告位/公告/设置/审计，见 docs/12）
-- ============================================================
CREATE DATABASE IF NOT EXISTS examforge_admin DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE examforge_admin;

CREATE TABLE IF NOT EXISTS `ad` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `position`   VARCHAR(30) NOT NULL COMMENT 'home_hero/home_banner/sidebar_teacher/sidebar_student/list_inline/detail_footer/login_promo',
  `title`      VARCHAR(100),
  `image_url`  VARCHAR(500) COMMENT '空则前台渲染占位框',
  `link_url`   VARCHAR(500),
  `audience`   VARCHAR(10) NOT NULL DEFAULT 'ALL' COMMENT 'ALL/TEACHER/STUDENT',
  `sort`       INT NOT NULL DEFAULT 0,
  `status`     TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_pos_status` (`position`,`status`)
) ENGINE=InnoDB COMMENT='广告位';

CREATE TABLE IF NOT EXISTS `notice` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `title`      VARCHAR(200) NOT NULL,
  `content`    TEXT,
  `audience`   VARCHAR(10) NOT NULL DEFAULT 'ALL',
  `status`     TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='公告';

CREATE TABLE IF NOT EXISTS `setting` (
  `key`   VARCHAR(50) PRIMARY KEY,
  `value` VARCHAR(500)
) ENGINE=InnoDB COMMENT='系统设置（logo_url 留空=前台占位）';

CREATE TABLE IF NOT EXISTS `audit_log` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`    BIGINT,
  `actor`      VARCHAR(50),
  `action`     VARCHAR(50) NOT NULL,
  `detail`     VARCHAR(2000),
  `ip`         VARCHAR(64),
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_time` (`created_at`)
) ENGINE=InnoDB COMMENT='操作审计（只读）';

INSERT INTO `setting`(`key`,`value`) VALUES
 ('site_name','智卷云'),('logo_url',''),('beian','苏ICP备XXXXXXXX号'),('service_phone','400-xxx-xxxx'),
 ('default_stage','高中'),('default_subject','高中数学')
ON DUPLICATE KEY UPDATE value=VALUES(value);
