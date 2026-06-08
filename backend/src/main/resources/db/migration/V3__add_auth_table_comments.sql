COMMENT ON TABLE users IS '사용자';
COMMENT ON COLUMN users.id IS '사용자 ID';
COMMENT ON COLUMN users.email IS '이메일';
COMMENT ON COLUMN users.email_canonical IS '정규화 이메일';
COMMENT ON COLUMN users.nickname IS '닉네임';
COMMENT ON COLUMN users.role IS '권한';
COMMENT ON COLUMN users.status IS '계정 상태';
COMMENT ON COLUMN users.email_verified_at IS '이메일 인증 일시';
COMMENT ON COLUMN users.created_at IS '생성 일시';
COMMENT ON COLUMN users.updated_at IS '수정 일시';

COMMENT ON TABLE local_credentials IS '로컬 로그인 인증 정보';
COMMENT ON COLUMN local_credentials.user_id IS '사용자 ID';
COMMENT ON COLUMN local_credentials.login_id IS '로그인 ID';
COMMENT ON COLUMN local_credentials.password_hash IS '비밀번호 해시';
COMMENT ON COLUMN local_credentials.password_updated_at IS '비밀번호 변경 일시';
COMMENT ON COLUMN local_credentials.failed_login_count IS '로그인 실패 횟수';
COMMENT ON COLUMN local_credentials.locked_until IS '계정 잠금 해제 일시';
COMMENT ON COLUMN local_credentials.created_at IS '생성 일시';
COMMENT ON COLUMN local_credentials.updated_at IS '수정 일시';

COMMENT ON TABLE oauth_accounts IS 'OAuth2 계정 연결';
COMMENT ON COLUMN oauth_accounts.id IS 'OAuth2 계정 ID';
COMMENT ON COLUMN oauth_accounts.user_id IS '사용자 ID';
COMMENT ON COLUMN oauth_accounts.provider IS 'OAuth2 제공자';
COMMENT ON COLUMN oauth_accounts.provider_user_id IS '제공자 사용자 ID';
COMMENT ON COLUMN oauth_accounts.provider_email IS '제공자 이메일';
COMMENT ON COLUMN oauth_accounts.provider_email_verified IS '제공자 이메일 인증 여부';
COMMENT ON COLUMN oauth_accounts.created_at IS '생성 일시';
COMMENT ON COLUMN oauth_accounts.updated_at IS '수정 일시';

COMMENT ON TABLE refresh_tokens IS '리프레시 토큰';
COMMENT ON COLUMN refresh_tokens.id IS '리프레시 토큰 ID';
COMMENT ON COLUMN refresh_tokens.user_id IS '사용자 ID';
COMMENT ON COLUMN refresh_tokens.token_hash IS '리프레시 토큰 해시';
COMMENT ON COLUMN refresh_tokens.expires_at IS '만료 일시';
COMMENT ON COLUMN refresh_tokens.revoked_at IS '폐기 일시';
COMMENT ON COLUMN refresh_tokens.created_at IS '생성 일시';

COMMENT ON TABLE auth_email_tokens IS '인증 이메일 토큰';
COMMENT ON COLUMN auth_email_tokens.id IS '인증 이메일 토큰 ID';
COMMENT ON COLUMN auth_email_tokens.user_id IS '사용자 ID';
COMMENT ON COLUMN auth_email_tokens.email IS '대상 이메일';
COMMENT ON COLUMN auth_email_tokens.token_hash IS '인증 토큰 해시';
COMMENT ON COLUMN auth_email_tokens.purpose IS '토큰 목적';
COMMENT ON COLUMN auth_email_tokens.expires_at IS '만료 일시';
COMMENT ON COLUMN auth_email_tokens.used_at IS '사용 일시';
COMMENT ON COLUMN auth_email_tokens.created_at IS '생성 일시';

COMMENT ON TABLE auth_email_logs IS '인증 이메일 요청 로그';
COMMENT ON COLUMN auth_email_logs.id IS '인증 이메일 요청 로그 ID';
COMMENT ON COLUMN auth_email_logs.email IS '대상 이메일';
COMMENT ON COLUMN auth_email_logs.purpose IS '요청 목적';
COMMENT ON COLUMN auth_email_logs.requested_ip IS '요청 IP';
COMMENT ON COLUMN auth_email_logs.created_at IS '생성 일시';
