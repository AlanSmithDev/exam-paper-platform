-- ============================================================
-- 智卷云 02: 题库库 examforge_question（学段→学科→章节→题目，见 docs/04/11）
-- ============================================================
CREATE DATABASE IF NOT EXISTS examforge_question DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE examforge_question;

CREATE TABLE IF NOT EXISTS `stage` (
  `id` TINYINT PRIMARY KEY COMMENT '1小学 2初中 3高中 4中职 5大学 6考研',
  `name` VARCHAR(20) NOT NULL, `sort` INT DEFAULT 0
) ENGINE=InnoDB COMMENT='学段';

CREATE TABLE IF NOT EXISTS `subject` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `stage_id` TINYINT NOT NULL, `name` VARCHAR(30) NOT NULL, `version` VARCHAR(50),
  UNIQUE KEY `uk_stage_name` (`stage_id`,`name`)
) ENGINE=InnoDB COMMENT='学科（学段×学科 唯一）';

CREATE TABLE IF NOT EXISTS `catalog` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `subject_id` BIGINT NOT NULL, `parent_id` BIGINT DEFAULT 0,
  `name` VARCHAR(120) NOT NULL, `sort` INT DEFAULT 0,
  KEY `idx_sub_parent` (`subject_id`,`parent_id`)
) ENGINE=InnoDB COMMENT='教材章节树';

CREATE TABLE IF NOT EXISTS `question` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `subject_id`  BIGINT      NOT NULL COMMENT '分片路由键（docs/04：按 subject 分域）',
  `type`        VARCHAR(10) NOT NULL,
  `difficulty`  TINYINT     NOT NULL COMMENT '1~5',
  `coefficient` DECIMAL(4,3) NOT NULL COMMENT '难度系数 0~1',
  `scene`       VARCHAR(20),
  `category`    VARCHAR(20) COMMENT '典型题/压轴题/同步题/易错题/常考题/好题/新定义',
  `kp_names`    VARCHAR(300) COMMENT '知识点（冗余，正式版拆关联表）',
  `literacy`    VARCHAR(200) COMMENT '核心素养',
  `source`      VARCHAR(200),
  `stem`        TEXT        NOT NULL COMMENT '题干 JSON Block（text/latex/figure）',
  `options`     JSON,
  `answer`      TEXT        NOT NULL,
  `analysis`    JSON COMMENT '{brief,solve,comment} 五段式精简',
  `author`      VARCHAR(50), `reviewer` VARCHAR(50),
  `status`      TINYINT     NOT NULL DEFAULT 2 COMMENT '0草稿 1审核中 2上架 3下架',
  `use_count`   INT         NOT NULL DEFAULT 0,
  `aigc`        TINYINT     NOT NULL DEFAULT 0,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_filter` (`subject_id`,`status`,`type`,`difficulty`),
  KEY `idx_use` (`use_count`)
) ENGINE=InnoDB COMMENT='题目主表（生产分片：16库×64表，见 docs/04 §8）';

-- 学段与学科种子
INSERT INTO `stage`(`id`,`name`,`sort`) VALUES (1,'小学',1),(2,'初中',2),(3,'高中',3),(4,'中职',4),(5,'大学',5),(6,'考研',6)
ON DUPLICATE KEY UPDATE name=VALUES(name);
INSERT INTO `subject`(`stage_id`,`name`,`version`) VALUES
 (3,'数学','人教A版(2019)'),(3,'物理','人教版(2019)'),(3,'化学','人教版(2019)'),(3,'生物','人教版(2019)'),
 (2,'数学','人教版(2024)'),(1,'数学','人教版'),(6,'数学','考研统考'),(6,'计算机','408统考'),(5,'高等数学','同济版')
ON DUPLICATE KEY UPDATE version=VALUES(version);

-- 高中数学示例题 2 道（正式题库由运营后台录入）
INSERT INTO `question`(`subject_id`,`type`,`difficulty`,`coefficient`,`scene`,`category`,`kp_names`,`literacy`,`source`,`stem`,`options`,`answer`,`analysis`)
SELECT s.id,'单选题',3,0.65,'阶段检测','常考题','双曲线,离心率','数学运算能力','2025·新高考Ⅱ卷改编',
 '双曲线 \\(\\dfrac{x^{2}}{4}-\\dfrac{y^{2}}{2}=1\\) 的离心率为（\\quad）',
 JSON_ARRAY(JSON_OBJECT('l','A','v','\\(\\dfrac{\\sqrt6}{2}\\)'),JSON_OBJECT('l','B','v','\\(\\dfrac{\\sqrt3}{2}\\)'),
            JSON_OBJECT('l','C','v','\\(\\sqrt2\\)'),JSON_OBJECT('l','D','v','\\(\\dfrac{\\sqrt6}{3}\\)')),
 'A', JSON_OBJECT('brief','a²=4,b²=2 ⇒ c²=6。','solve','e=√6/2。','comment','双曲线 c²=a²+b²，e=c/a。')
FROM `subject` s WHERE s.stage_id=3 AND s.name='数学'
ON DUPLICATE KEY UPDATE stem=VALUES(stem);
