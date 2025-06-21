-- ============================================================
-- 智卷云 10: 交易库增量（用户增长域：CDK 激活码 / 积分任务 / 天天领券）
-- 对应 docs/26 T-26d（F-XKW-06/07）与 docs/14 营销域扩展
-- ============================================================
USE examforge_trade;

-- ---------- CDK 激活码批次 ----------
CREATE TABLE IF NOT EXISTS `cdk_batch` (
  `id`                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  `batch_no`           VARCHAR(32) NOT NULL COMMENT '批次号 CDK{yyyymmddHHMMss}{4位随机}',
  `reward_type`        VARCHAR(20) NOT NULL COMMENT 'MEMBER_DAYS/POINTS/COUPON',
  `reward_days`        INT NULL COMMENT 'MEMBER_DAYS: 会员天数(1~366)',
  `reward_points`      INT NULL COMMENT 'POINTS: 点数(1~100000)',
  `reward_template_id` BIGINT NULL COMMENT 'COUPON: 关联券模板',
  `total`              INT NOT NULL COMMENT '生成张数(1~5000)',
  `redeemed`           INT NOT NULL DEFAULT 0 COMMENT '已兑换数(条件更新防超兑)',
  `expire_time`        DATETIME NULL COMMENT '整批失效时间(NULL=永久)',
  `status`             TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `operator`           VARCHAR(64) NOT NULL DEFAULT 'admin',
  `created_at`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB COMMENT='CDK 批次（生成/停用/余量对账）';

-- ---------- CDK 激活码 ----------
CREATE TABLE IF NOT EXISTS `cdk_code` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `code`       CHAR(14) NOT NULL COMMENT '兑换码 XXXX-XXXX-XXXX（去歧义字符集）',
  `batch_id`   BIGINT NOT NULL,
  `status`     TINYINT NOT NULL DEFAULT 0 COMMENT '0未用 1已用',
  `used_by`    BIGINT NULL,
  `used_at`    DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_batch` (`batch_id`, `status`)
) ENGINE=InnoDB COMMENT='CDK 激活码（一码一用，条件更新防并发重用）';

-- ---------- 积分任务 ----------
CREATE TABLE IF NOT EXISTS `task_def` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `task_key`      VARCHAR(40) NOT NULL COMMENT 'DAILY_LOGIN/FIRST_COMPOSE/FIRST_UPLOAD/INVITE_FRIEND/AI_FIRST_USE…',
  `name`          VARCHAR(50) NOT NULL,
  `reward_points` INT NOT NULL COMMENT '奖励点数',
  `daily`         TINYINT NOT NULL DEFAULT 0 COMMENT '1每日任务(周期=当天) 0一次性(周期=LIFETIME)',
  `status`        TINYINT NOT NULL DEFAULT 1,
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_task_key` (`task_key`)
) ENGINE=InnoDB COMMENT='积分任务定义（运营可配，docs/26 F-XKW-07）';

CREATE TABLE IF NOT EXISTS `task_record` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`    BIGINT NOT NULL,
  `task_key`   VARCHAR(40) NOT NULL,
  `period`     CHAR(8) NOT NULL COMMENT '每日任务=yyyymmdd，一次性=LIFETIME',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user_task_period` (`user_id`, `task_key`, `period`),
  KEY `idx_user` (`user_id`, `created_at`)
) ENGINE=InnoDB COMMENT='任务完成记录（唯一索引幂等，完成即发奖）';

-- ---------- 天天领券：券模板增加每日每人限领 ----------
-- MySQL 8.0 无 ADD COLUMN IF NOT EXISTS：依赖初始化卷一次性执行（重复执行前请先核对列是否存在）
ALTER TABLE `coupon_template`
  ADD COLUMN `daily_per_limit` INT NOT NULL DEFAULT 0
  COMMENT '每人每日限领(0=不限,≥1=天天领券类)' AFTER `per_limit`;

-- ---------- 种子：积分任务定义 ----------
INSERT INTO `task_def`(`task_key`,`name`,`reward_points`,`daily`) VALUES
 ('DAILY_LOGIN','每日登录',1,1),
 ('FIRST_COMPOSE','完成首次组卷',10,0),
 ('FIRST_UPLOAD','首次录题上架（含AI变式过审）',20,0),
 ('INVITE_FRIEND','邀请好友注册成功',50,0),
 ('AI_FIRST_USE','首次使用 AI 讲题/变式',5,0)
ON DUPLICATE KEY UPDATE name=VALUES(name), reward_points=VALUES(reward_points), daily=VALUES(daily);
