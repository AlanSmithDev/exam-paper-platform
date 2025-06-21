-- ============================================================
-- 智卷云 09: 邀请裂变（docs/16 §1，user 库扩展）
-- ============================================================
USE examforge_user;

ALTER TABLE `user`
  ADD COLUMN `invite_code` VARCHAR(12) NULL COMMENT '我的邀请码（6位base36）',
  ADD COLUMN `invited_by`  BIGINT NULL COMMENT '邀请人 user_id',
  ADD UNIQUE KEY `uk_invite_code` (`invite_code`),
  ADD KEY `idx_invited_by` (`invited_by`);
