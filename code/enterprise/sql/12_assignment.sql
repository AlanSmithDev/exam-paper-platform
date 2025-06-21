-- ============================================================
-- ExamForge 12: 作业域（e 卷通第一步：布置→作答→判分→班级报告）
-- 对应 docs/26 F-XKW-12（T-26g 首期）与 docs/25 教师端班级域
-- ============================================================
USE examforge_practice;

CREATE TABLE IF NOT EXISTS `assignment` (
  `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
  `teacher_id`   BIGINT NOT NULL,
  `title`        VARCHAR(200) NOT NULL,
  `subject_id`   BIGINT,
  `question_ids` TEXT NOT NULL COMMENT '题目ID JSON 数组',
  `deadline`     DATETIME NULL COMMENT '截止时间（NULL=不限）',
  `status`       TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布 2已关闭',
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_teacher` (`teacher_id`, `status`)
) ENGINE=InnoDB COMMENT='教师作业';

CREATE TABLE IF NOT EXISTS `assignment_student` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `assignment_id` BIGINT NOT NULL,
  `student_id`    BIGINT NOT NULL,
  `status`        TINYINT NOT NULL DEFAULT 0 COMMENT '0待完成 1已提交 2已批改',
  `score`         DECIMAL(5,2) COMMENT '正确率(%)，解答题批改后更新',
  `late`          TINYINT NOT NULL DEFAULT 0 COMMENT '超时提交标记',
  `submit_at`     DATETIME NULL,
  `graded_at`     DATETIME NULL,
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_assign_student` (`assignment_id`, `student_id`),
  KEY `idx_student` (`student_id`, `status`)
) ENGINE=InnoDB COMMENT='学生作业名单（一作业一学生一行，唯一索引幂等）';

CREATE TABLE IF NOT EXISTS `assignment_answer` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `assignment_id` BIGINT NOT NULL,
  `student_id`    BIGINT NOT NULL,
  `question_id`   BIGINT NOT NULL,
  `answer`        TEXT,
  `correct`       TINYINT COMMENT '1对 0错 NULL待人工批改（解答题）',
  `score`         DECIMAL(5,2) COMMENT '教师批改给分（解答题）',
  `duration_ms`   INT DEFAULT 0,
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_row` (`assignment_id`, `student_id`, `question_id`),
  KEY `idx_question` (`assignment_id`, `question_id`)
) ENGINE=InnoDB COMMENT='作业作答明细（重交覆盖：条件更新）';
