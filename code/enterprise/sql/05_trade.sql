-- ============================================================
-- 智卷云 05: 交易库 examforge_trade（会员/点数/优惠券/订单/支付/下载计费，docs/14）
-- ============================================================
CREATE DATABASE IF NOT EXISTS examforge_trade DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE examforge_trade;

CREATE TABLE IF NOT EXISTS `member_plan` (
  `plan_id`       VARCHAR(30) PRIMARY KEY COMMENT 'TEACHER_PRO/SINGLE/STUDENT/SCHOOL',
  `name`          VARCHAR(50) NOT NULL,
  `price_cents`   INT NOT NULL COMMENT '价格（分）',
  `duration_days` INT NOT NULL,
  `benefits`      JSON COMMENT '权益描述',
  `status`        TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB COMMENT='会员档位（后台可配）';

CREATE TABLE IF NOT EXISTS `member` (
  `user_id`     BIGINT PRIMARY KEY,
  `plan_id`     VARCHAR(30),
  `expire_time` DATETIME,
  `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='会员';

CREATE TABLE IF NOT EXISTS `point_account` (
  `user_id` BIGINT PRIMARY KEY,
  `balance` INT NOT NULL DEFAULT 0 COMMENT '点数（1点=1分钱等值）',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁'
) ENGINE=InnoDB COMMENT='点数账户';

CREATE TABLE IF NOT EXISTS `point_log` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`    BIGINT NOT NULL,
  `change_val` INT NOT NULL COMMENT '正增负减',
  `reason`     VARCHAR(30) NOT NULL COMMENT 'RECHARGE/DOWNLOAD/REWARD/Coupon/ADMIN/REFUND',
  `ref`        VARCHAR(64),
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_user` (`user_id`,`created_at`)
) ENGINE=InnoDB COMMENT='点数流水';

CREATE TABLE IF NOT EXISTS `coupon_template` (
  `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
  `name`            VARCHAR(100) NOT NULL,
  `type`            VARCHAR(20) NOT NULL COMMENT 'FULL_REDUCTION/DISCOUNT/POINTS',
  `discount_cents`  INT NOT NULL DEFAULT 0 COMMENT '满减金额或 POINTS 点数',
  `min_spend_cents` INT NOT NULL DEFAULT 0 COMMENT '使用门槛',
  `discount_rate`   DECIMAL(3,2) COMMENT '折扣率（DISCOUNT 用）',
  `rate_cap_cents`  INT COMMENT '折扣封顶',
  `total`           INT NOT NULL DEFAULT 0 COMMENT '发放总量',
  `granted`         INT NOT NULL DEFAULT 0,
  `per_limit`       INT NOT NULL DEFAULT 1 COMMENT '每人限领',
  `valid_days`      INT COMMENT '领后有效天数',
  `status`          TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB COMMENT='券模板';

CREATE TABLE IF NOT EXISTS `user_coupon` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `template_id` BIGINT NOT NULL,
  `user_id`     BIGINT NOT NULL,
  `status`      TINYINT NOT NULL DEFAULT 0 COMMENT '0未用 1已用 2过期',
  `expire_time` DATETIME,
  `used_order`  VARCHAR(64),
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_user_status` (`user_id`,`status`)
) ENGINE=InnoDB COMMENT='用户券';

CREATE TABLE IF NOT EXISTS `trade_order` (
  `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
  `order_no`         VARCHAR(64) NOT NULL,
  `user_id`          BIGINT NOT NULL,
  `sku_type`         VARCHAR(20) NOT NULL COMMENT 'MEMBER/POINTS/PAPER',
  `sku_ref`          VARCHAR(30) COMMENT 'plan_id 或点数包',
  `quantity`         INT NOT NULL DEFAULT 1,
  `amount_cents`     INT NOT NULL COMMENT '原价',
  `discount_cents`   INT NOT NULL DEFAULT 0,
  `pay_cents`        INT NOT NULL COMMENT '实付',
  `coupon_id`        BIGINT,
  `status`           VARCHAR(20) NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/PAID/CLOSED/REFUNDED',
  `idempotency_key`  VARCHAR(64) NOT NULL,
  `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `paid_at`          DATETIME,
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_idem` (`idempotency_key`),
  KEY `idx_user` (`user_id`,`status`)
) ENGINE=InnoDB COMMENT='交易订单';

CREATE TABLE IF NOT EXISTS `payment_callback` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `order_no`   VARCHAR(64) NOT NULL,
  `provider`   VARCHAR(20) NOT NULL,
  `payload`    VARCHAR(2000),
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_order` (`order_no`)
) ENGINE=InnoDB COMMENT='支付回调流水（幂等依据）';

CREATE TABLE IF NOT EXISTS `download_record` (
  `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`        BIGINT NOT NULL,
  `paper_hash`     VARCHAR(64) NOT NULL COMMENT '试卷内容指纹（30天重复下载判定）',
  `question_count` INT NOT NULL,
  `charged`        VARCHAR(20) NOT NULL COMMENT 'FREE/MEMBER/POINTS',
  `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_user_hash` (`user_id`,`paper_hash`)
) ENGINE=InnoDB COMMENT='下载记录';

CREATE TABLE IF NOT EXISTS `sign_record` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`    BIGINT NOT NULL,
  `sign_date`  DATE NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user_date` (`user_id`,`sign_date`)
) ENGINE=InnoDB COMMENT='签到日历（防重复，docs/14 K-3）';

-- 种子：会员档位（对标组卷网实测，价格更优）+ 新人券模板
INSERT INTO `member_plan`(`plan_id`,`name`,`price_cents`,`duration_days`,`benefits`) VALUES
 ('TEACHER_PRO','教师会员（下载不限次+AI配额）',2500,30, JSON_OBJECT('unlimitedDownload',TRUE,'aiDaily',50)),
 ('SINGLE','单科会员（单学段单科全免）',3900,30, JSON_OBJECT('singleSubjectFree',TRUE)),
 ('STUDENT','学生会员',1500,30, JSON_OBJECT('aiExplainDaily',200)),
 ('POINTS_1000','点数充值 1000',1000,0, JSON_OBJECT('points',1000))
ON DUPLICATE KEY UPDATE name=VALUES(name);
