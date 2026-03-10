-- OAuth 소셜 로그인 지원을 위한 컬럼 추가
-- provider: GOOGLE / NAVER / KAKAO (로컬 계정은 NULL)
-- provider_id: 각 제공자의 고유 사용자 ID
-- provider_key: GENERATED 컬럼 — (provider, provider_id) 조합의 UNIQUE 보장
--   NULL 허용: MySQL UNIQUE 인덱스는 NULL을 중복으로 보지 않아 로컬 계정 다수 허용
-- password: OAuth 계정은 비밀번호 없음 → NULL 허용으로 변경
ALTER TABLE members
    ADD COLUMN provider    VARCHAR(20)  NULL COMMENT 'OAuth 제공자 (GOOGLE/NAVER/KAKAO)',
    ADD COLUMN provider_id VARCHAR(255) NULL COMMENT 'OAuth 제공자 사용자 ID',
    ADD COLUMN provider_key VARCHAR(280) GENERATED ALWAYS AS (
        IF(provider IS NOT NULL, CONCAT(provider, ':', provider_id), NULL)
    ) STORED COMMENT '(provider, provider_id) 복합 유일 키',
    MODIFY COLUMN password VARCHAR(255) NULL,
    ADD UNIQUE KEY uk_members_provider_key (provider_key);
