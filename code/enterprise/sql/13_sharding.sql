-- ============================================================
-- ExamForge 13: question 分表落地（G2 首期：先分表后分库，docs/27 §9.2 定稿路由）
-- 路由：question_${id % 8}（8 逻辑槽；扩到 16 库只改映射不改路由）
-- ⚠️ 执行时机：停写窗口内（docs/19 §4 变更窗口，避开模考季）
-- ============================================================
USE examforge_question;

-- ---------- 1) 建 8 张分表（结构跟随 question 主表） ----------
CREATE TABLE IF NOT EXISTS `question_0` LIKE `question`;
CREATE TABLE IF NOT EXISTS `question_1` LIKE `question`;
CREATE TABLE IF NOT EXISTS `question_2` LIKE `question`;
CREATE TABLE IF NOT EXISTS `question_3` LIKE `question`;
CREATE TABLE IF NOT EXISTS `question_4` LIKE `question`;
CREATE TABLE IF NOT EXISTS `question_5` LIKE `question`;
CREATE TABLE IF NOT EXISTS `question_6` LIKE `question`;
CREATE TABLE IF NOT EXISTS `question_7` LIKE `question`;

-- ---------- 2) 存量数据按 id % 8 迁移（INSERT IGNORE 幂等，可重复执行） ----------
INSERT IGNORE INTO `question_0` SELECT * FROM `question` WHERE MOD(id, 8) = 0;
INSERT IGNORE INTO `question_1` SELECT * FROM `question` WHERE MOD(id, 8) = 1;
INSERT IGNORE INTO `question_2` SELECT * FROM `question` WHERE MOD(id, 8) = 2;
INSERT IGNORE INTO `question_3` SELECT * FROM `question` WHERE MOD(id, 8) = 3;
INSERT IGNORE INTO `question_4` SELECT * FROM `question` WHERE MOD(id, 8) = 4;
INSERT IGNORE INTO `question_5` SELECT * FROM `question` WHERE MOD(id, 8) = 5;
INSERT IGNORE INTO `question_6` SELECT * FROM `question` WHERE MOD(id, 8) = 6;
INSERT IGNORE INTO `question_7` SELECT * FROM `question` WHERE MOD(id, 8) = 7;

-- ---------- 3) 对账（每行应相等；任一不等即中止切换） ----------
-- SELECT (SELECT COUNT(*) FROM question) AS src,
--        (SELECT COUNT(*) FROM question_0)+ (SELECT COUNT(*) FROM question_1)
--      + (SELECT COUNT(*) FROM question_2)+ (SELECT COUNT(*) FROM question_3)
--      + (SELECT COUNT(*) FROM question_4)+ (SELECT COUNT(*) FROM question_5)
--      + (SELECT COUNT(*) FROM question_6)+ (SELECT COUNT(*) FROM question_7) AS dst;

-- ---------- 4) 切换（对账通过后执行） ----------
-- 4a) 应用切 profile：docker-compose 环境变量 QUESTION_PROFILE=sharding（重启 question 服务）
-- 4b) 主表改名归档（ShardingSphere 逻辑表 question 将路由到 question_0..7，原名必须让位）：
-- RENAME TABLE `question` TO `question_legacy`;

-- ---------- 5) 回滚预案 ----------
-- RENAME TABLE `question_legacy` TO `question`;
-- 应用环境变量 QUESTION_PROFILE 置空并重启（回到单表模式）
-- 分表数据保留（迁移幂等，不丢数据）
