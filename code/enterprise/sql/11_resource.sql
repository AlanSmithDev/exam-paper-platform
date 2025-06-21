-- ============================================================
-- 智卷云 11: 资源库 examforge_resource（资源中心 P1 + 版权服务中心）
-- 对应 docs/26 F-XKW-01/02/03/14（T-26b/T-26c）
-- ============================================================
CREATE DATABASE IF NOT EXISTS examforge_resource DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE examforge_resource;

CREATE TABLE IF NOT EXISTS `resource_item` (
  `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
  `title`           VARCHAR(200) NOT NULL,
  `stage`           TINYINT NOT NULL COMMENT '1小学 2初中 3高中 4大学 5考研 6中职',
  `subject`         VARCHAR(30) NOT NULL,
  `category`        VARCHAR(20) NOT NULL COMMENT 'PPT课件/教案/学案/作业/试卷/题集/素材/示范课',
  `grade`           VARCHAR(20),
  `format`          VARCHAR(10) NOT NULL COMMENT 'pptx/docx/pdf/zip/mp4',
  `pages`           INT NOT NULL DEFAULT 0 COMMENT '页数（预览计算基准）',
  `size_kb`         INT NOT NULL DEFAULT 0,
  `level`           VARCHAR(10) NOT NULL DEFAULT 'NORMAL' COMMENT 'FREE免费/NORMAL普通/SPECIAL特供/BOUTIQUE精品',
  `price_cents`     INT NOT NULL DEFAULT 200 COMMENT '下载价（分），学科网实测单份 ¥2',
  `author`          VARCHAR(64),
  `school`          VARCHAR(100) COMMENT '来源学校/工作室',
  `preview_free_pct` TINYINT NOT NULL DEFAULT 33 COMMENT '免费预览百分比（学科网实测 33%）',
  `file_key`        VARCHAR(200) COMMENT 'OSS对象键（演示环境为占位）',
  `browse_count`    INT NOT NULL DEFAULT 0,
  `download_count`  INT NOT NULL DEFAULT 0,
  `status`          TINYINT NOT NULL DEFAULT 0 COMMENT '0待审 1上架 2下架 3驳回',
  `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_browse` (`stage`,`subject`,`category`,`status`),
  KEY `idx_level` (`level`,`status`)
) ENGINE=InnoDB COMMENT='资源（课件/教案/学案等文档资产）';

CREATE TABLE IF NOT EXISTS `resource_basket` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`     BIGINT NOT NULL,
  `resource_id` BIGINT NOT NULL,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user_resource` (`user_id`,`resource_id`)
) ENGINE=InnoDB COMMENT='资源篮（购物车，docs/26 F-XKW-03）';

CREATE TABLE IF NOT EXISTS `resource_download` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`     BIGINT NOT NULL,
  `resource_id` BIGINT NOT NULL,
  `mode`        VARCHAR(10) NOT NULL COMMENT 'FREE/POINTS（重复下载免费）',
  `price_cents` INT NOT NULL DEFAULT 0,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_user_res` (`user_id`,`resource_id`)
) ENGINE=InnoDB COMMENT='资源下载记录（已购判定依据）';

CREATE TABLE IF NOT EXISTS `copyright_appeal` (
  `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`      BIGINT NOT NULL,
  `target_type`  VARCHAR(10) NOT NULL COMMENT 'RESOURCE/QUESTION/PAPER',
  `target_id`    BIGINT NOT NULL,
  `appeal_type`  VARCHAR(20) NOT NULL COMMENT 'COPYRIGHT版权异议/ERROR内容错误/OTHER其他',
  `content`      VARCHAR(1000) NOT NULL,
  `contact`      VARCHAR(100),
  `status`       VARCHAR(12) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/RESOLVED/REJECTED/WITHDRAWN',
  `handle_remark` VARCHAR(500),
  `handler`      VARCHAR(64),
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_at`  DATETIME NULL,
  KEY `idx_status` (`status`,`created_at`),
  KEY `idx_target` (`target_type`,`target_id`)
) ENGINE=InnoDB COMMENT='版权异议/纠错申诉工单（docs/26 F-XKW-14 合规必备）';

-- 种子：覆盖各类别与等级
INSERT INTO `resource_item`(`title`,`stage`,`subject`,`category`,`grade`,`format`,`pages`,`level`,`price_cents`,`author`,`school`,`status`,`file_key`) VALUES
 ('2.2 基本不等式同步课件（人教A版2019必修第一册）',3,'数学','PPT课件','高一','pptx',48,'NORMAL',200,'学科网数编组','示范工作室',1,'seed/sx-2-2.pptx'),
 ('1.1 集合的概念教案+学案打包（人教A版2019）',3,'数学','教案','高一','zip',12,'FREE',0,'学科网数编组','示范工作室',1,'seed/sx-1-1.zip'),
 ('《归园田居（其一）》精品课件 18张（统编版必修上册）',3,'语文','PPT课件','高一','pptx',18,'BOUTIQUE',500,'珠溪语文','珠溪工作室',1,'seed/yw-gytfj.pptx'),
 ('八年级上册物理第一次月考试卷（含答案解析）',2,'物理','试卷','初二','docx',8,'NORMAL',200,'勤勉理科','勤勉资料库',1,'seed/wl-yk.docx'),
 ('中考英语完形填空专项训练（题集）',2,'英语','题集','初三','pdf',32,'SPECIAL',300,'学科网英编组','示范工作室',1,'seed/yy-wxtk.pdf')
ON DUPLICATE KEY UPDATE title=VALUES(title);
