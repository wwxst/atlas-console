package com.web.project.admin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SysUserTableNamingTest {

    private final Path projectRoot = Path.of(System.getProperty("user.dir"));

    @Test
    void freshSchemaAndMapperUseSysUserTableName() throws IOException {
        String schema = read("src/main/resources/db/migration/V1__initial_schema.sql");
        String mapper = read("src/main/resources/mapper/admin/SysUserMapper.xml");

        assertTrue(schema.contains("CREATE TABLE sys_user"));
        assertTrue(schema.contains("uk_sys_user_username"));
        assertTrue(schema.contains("auth_version BIGINT NOT NULL DEFAULT 0"));
        assertTrue(schema.contains("INSERT INTO sys_user (username, password, nickname, status)"));
        assertFalse(schema.contains("admin_user"));
        assertFalse(mapper.contains("admin_user"));
        assertTrue(mapper.contains("FROM sys_user"));
    }

    @Test
    void systemAvatarUsesFileIdInSchemaAndMapper() throws IOException {
        String schema = read("src/main/resources/db/migration/V1__initial_schema.sql");
        String mapper = read("src/main/resources/mapper/admin/SysUserMapper.xml");
        assertTrue(schema.contains("avatar_file_id"));
        assertTrue(mapper.contains("avatar_file_id"));
        assertFalse(mapper.contains("SET avatar ="));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
