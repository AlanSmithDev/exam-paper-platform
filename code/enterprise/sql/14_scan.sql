-- ============================================================
-- ExamForge 14: 扫描阅卷首期（e 卷通二阶段：扫描件=批改证据层）
-- docs/26 §7：扫描上传 → 预览对照 → 转录作答 → 自动判分（OCR 识别为 P3 AI 视觉钩子）
-- ============================================================
USE examforge_practice;

CREATE TABLE IF NOT EXISTS `scan_record` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `assignment_id` BIGINT NOT NULL,
  `student_id`    BIGINT NOT NULL,
  `file_name`     VARCHAR(200) NOT NULL COMMENT '原始文件名',
  `stored_path`   VARCHAR(300) NOT NULL COMMENT '存储相对路径（本地 tmp/OSS key）',
  `mime`          VARCHAR(50) NOT NULL,
  `size_bytes`    INT NOT NULL,
  `status`        VARCHAR(16) NOT NULL DEFAULT 'UPLOADED' COMMENT 'UPLOADED/RECOGNIZED/IMPORTED',
  `ocr_json`      TEXT COMMENT '识别结果（P3：AI 视觉输出 [{questionId,answer}]）',
  `uploaded_by`   BIGINT NOT NULL,
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_assign_student` (`assignment_id`, `student_id`, `created_at`)
) ENGINE=InnoDB COMMENT='扫描件记录（批改证据层，一学生可传多页）';
