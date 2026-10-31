package com.web.project.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ATLAS_POSTGRES_TEST_URL", matches = "jdbc:postgresql:.*")
@SpringBootTest(properties = {
        "spring.datasource.url=${ATLAS_POSTGRES_TEST_URL}",
        "spring.flyway.enabled=true",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class DatabaseMigrationIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void freshDatabaseIsCreatedAndTrackedByFlyway() {
        flyway.validate();

        assertNotNull(flyway.info().current());
        assertEquals("1", flyway.info().current().getVersion().getVersion());

        Integer tableCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name IN ('sys_user', 'user_account', 'sys_config', 'storage_space', 'storage_file')
                """, Integer.class);
        assertEquals(5, tableCount);
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND indexname = 'uk_sys_user_username'
                  AND indexdef ILIKE '%lower%username%'
                """, Integer.class), "Username uniqueness must remain case-insensitive");

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = 'admin'", Integer.class));
        assertEquals(8, jdbc.queryForObject("SELECT COUNT(*) FROM sys_config", Integer.class));
        Boolean applicationRoleIsSuperuser = jdbc.queryForObject(
                "SELECT rolsuper FROM pg_roles WHERE rolname = current_user", Boolean.class);
        assertNotNull(applicationRoleIsSuperuser);
        assertFalse(applicationRoleIsSuperuser, "The application must never connect as a PostgreSQL superuser");
    }
}
