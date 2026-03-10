package dev.dnpm.etl.processor.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing
import org.springframework.security.core.context.SecurityContextHolder
import java.util.*

@Configuration
@EnableJdbcAuditing
class AuditConfiguration {

    @Bean
    fun audit(): AuditorAware<String> {
        return AuditorAware {
            Optional.of(SecurityContextHolder.getContext().authentication?.name ?: "SYSTEM")
        }
    }

}
