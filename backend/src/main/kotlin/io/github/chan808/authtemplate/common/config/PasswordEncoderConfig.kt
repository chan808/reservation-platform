package io.github.chan808.authtemplate.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class PasswordEncoderConfig {
    @Bean
    // BCrypt work factor 기본값(10): 인증 서버 부하와 보안 강도 균형 → 운영 환경 요건에 따라 조정
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
