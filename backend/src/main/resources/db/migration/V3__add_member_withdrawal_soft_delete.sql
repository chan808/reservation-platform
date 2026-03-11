ALTER TABLE members
    ADD COLUMN withdrawn_at DATETIME(6) NULL AFTER nickname,
    ADD INDEX idx_members_withdrawn_at (withdrawn_at);
