package com.atomichabits.backend.migration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the Flyway migrations on a real PostgreSQL 15 and boots the application with
 * {@code ddl-auto=validate}, exactly like the prod profile. Fails when an entity change is not
 * accompanied by a migration (or vice versa).
 */
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationIntegrationTest {

    private static EmbeddedPostgres postgres;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) throws IOException {
        postgres = EmbeddedPostgres.builder().start();
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @AfterAll
    static void stopPostgres() throws IOException {
        postgres.close();
    }

    @Test
    void migrationsApplyCleanlyAndMatchTheEntities() {
        // Reaching this point means Hibernate validated every entity against the migrated schema.
        assertThat(Arrays.stream(flyway.info().applied()).map(MigrationInfo::getVersion).map(Object::toString))
                .containsExactly("1", "2");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where indexname = 'idx_habits_user'", Integer.class))
                .isEqualTo(1);
    }
}
