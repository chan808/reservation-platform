-- emailVerified = false인 기존 계정은 이메일 재인증 필요
ALTER TABLE members
    ADD COLUMN email_verified TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '이메일 인증 여부: 미인증 계정은 로그인 불가';
