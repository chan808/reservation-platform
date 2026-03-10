-- 닉네임 컬럼 추가 (선택적 — NULL 허용)
ALTER TABLE members
    ADD COLUMN nickname VARCHAR(50) NULL COMMENT '닉네임 (미설정 시 NULL)';
