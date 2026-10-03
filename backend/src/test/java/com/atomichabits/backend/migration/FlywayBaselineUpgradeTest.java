package com.atomichabits.backend.migration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upgrade path for deployments that predate Flyway: their schema was created by
 * {@code ddl-auto=update} (equivalent to V1). With the prod settings Flyway must baseline it at
 * version 1 and apply only the later migrations, without touching existing tables.
 */
class FlywayBaselineUpgradeTest {

    @Test
    void existingSchemaIsBaselinedAndOnlyNewerMigrationsRun() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.builder().start()) {
            var dataSource = postgres.getPostgresDatabase();

            String legacySchema = new String(
                    getClass().getResourceAsStream("/db/migration/V1__init.sql").readAllBytes(), StandardCharsets.UTF_8);
            try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
                s.execute(legacySchema);
                s.execute("insert into users (email, password) values ('legacy@example.com', 'x')");
            }

            MigrateResult result = Flyway.configure()
                    .dataSource(dataSource)
                    .baselineOnMigrate(true)
                    .baselineVersion("1")
                    .load()
                    .migrate();

            assertThat(result.migrations).extracting(m -> m.version).containsExactly("2");
            try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("select count(*) from users")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }
}
