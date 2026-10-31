package com.web.project.persistence;

import com.web.project.admin.entity.SysUser;
import com.web.project.admin.mapper.SysUserMapper;
import com.web.project.channel.dto.AuthChannelUpdateDTO;
import com.web.project.channel.mapper.SysConfigMapper;
import com.web.project.channel.model.AuthChannel;
import com.web.project.channel.service.AuthChannelService;
import com.web.project.user.entity.UserAccount;
import com.web.project.user.mapper.UserAccountMapper;
import com.web.project.user.dto.UpdateUserAccountDTO;
import com.web.project.user.service.UserAccountService;
import com.web.project.common.exception.BusinessException;
import com.web.project.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 仅连接显式指定、按 web_project.sql 初始化的可丢弃 PostgreSQL 库。
 */
@EnabledIfEnvironmentVariable(named = "ATLAS_POSTGRES_TEST_URL", matches = "jdbc:postgresql:.*")
@SpringBootTest(properties = {
        "spring.datasource.url=${ATLAS_POSTGRES_TEST_URL}",
        "spring.flyway.enabled=true",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Transactional
class MapperIntegrationTest {

    @Autowired
    private UserAccountMapper userAccountMapper;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private SysConfigMapper sysConfigMapper;
    @Autowired
    private AuthChannelService authChannelService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserAccountService userAccountService;

    @Test
    void enableDisableAndRepeatedStatusWritesPreserveOtherAccountFields() {
        jdbcTemplate.update("""
                INSERT INTO user_account (id, user_no, phone, email, password, password_version, nickname, avatar)
                VALUES (1001, 'status-user', '13800138909', 'status-test@example.com', 'test-hash', 7,
                        '状态测试', '/avatar.png')
                """);
        Long id = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE user_no = 'status-user'", Long.class);
        UserAccount original = userAccountMapper.selectById(id);
        int statusWriteCount = 0;
        for (int status : List.of(0, 0, 1, 1)) {
            userAccountService.updateStatus(id, status);
            statusWriteCount++;
            UserAccount saved = userAccountMapper.selectById(id);
            assertEquals(status, saved.getStatus());
            assertEquals(original.getNickname(), saved.getNickname());
            assertEquals(original.getPhone(), saved.getPhone());
            assertEquals(original.getEmail(), saved.getEmail());
            assertEquals(original.getPassword(), saved.getPassword());
            // A status change (including re-enabling) permanently invalidates old tokens.
            assertEquals(original.getPasswordVersion() + statusWriteCount, saved.getPasswordVersion());
            assertEquals(original.getUserNo(), saved.getUserNo());
            assertEquals(original.getAvatar(), saved.getAvatar());
            assertEquals(original.getCreatedAt(), saved.getCreatedAt());
            assertEquals(1, userAccountMapper.selectPageByCondition("status-user", status, 0, 10, null).size());
            assertEquals(0, userAccountMapper.countByCondition("status-user", 1 - status));
        }
        assertThrows(BusinessException.class, () -> userAccountService.updateStatus(Long.MAX_VALUE, 0));
    }

    @Test
    void adminEditPreservesProtectedFieldsAndAllowsDisabledAccountAndContactRemoval() {
        jdbcTemplate.update("""
                INSERT INTO user_account (id, user_no, phone, email, password, password_version, nickname, avatar, status)
                VALUES (1002, 'admin-edit-user', '13800138901', 'before-edit@example.com', 'test-hash', 7,
                        '原昵称', '/avatar.png', 0)
                """);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM user_account WHERE user_no = 'admin-edit-user'", Long.class);
        UserAccount original = userAccountMapper.selectById(id);
        for (int attempt = 0; attempt < 2; attempt++) {
            var result = userAccountService.updateUser(id,
                    new UpdateUserAccountDTO(" 新昵称 ", null, " AFTER-EDIT@EXAMPLE.COM "));
            assertEquals("新昵称", result.nickname());
            assertNull(result.phone());
            assertEquals("after-edit@example.com", result.email());
            assertEquals(0, result.status());
        }
        UserAccount stored = userAccountMapper.selectById(id);
        assertEquals(original.getUserNo(), stored.getUserNo());
        assertEquals(original.getPassword(), stored.getPassword());
        assertEquals(original.getPasswordVersion(), stored.getPasswordVersion());
        assertEquals(original.getAvatar(), stored.getAvatar());
        assertEquals(original.getCreatedAt(), stored.getCreatedAt());
        assertNotNull(stored.getUpdatedAt());
        var phoneOnly = userAccountService.updateUser(id,
                new UpdateUserAccountDTO("新昵称", " 13800138902 ", null));
        assertEquals("13800138902", phoneOnly.phone());
        assertNull(phoneOnly.email());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void adminContactConflictsAreBusinessErrorsAndDoNotPartiallyUpdate() {
        jdbcTemplate.update("""
                INSERT INTO user_account (id, user_no, phone, email, nickname)
                VALUES (1003, 'edit-first', '13800138903', 'edit-first@example.com', '原昵称'),
                       (1004, 'edit-second', '13800138904', 'edit-second@example.com', '占用')
                """);
        Long id = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE user_no = 'edit-first'", Long.class);
        for (var dto : List.of(
                new UpdateUserAccountDTO("改动", "13800138904", "new-edit@example.com"),
                new UpdateUserAccountDTO("改动", "13800138905", "edit-second@example.com"))) {
            var failure = assertThrows(BusinessException.class, () -> userAccountService.updateUser(id, dto));
            assertEquals(ErrorCode.TARGET_ALREADY_BOUND, failure.getErrorCode());
            assertNotNull(failure.getCause());
            UserAccount stored = userAccountMapper.selectById(id);
            assertEquals("原昵称", stored.getNickname());
            assertEquals("13800138903", stored.getPhone());
            assertEquals("edit-first@example.com", stored.getEmail());
        }
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> userAccountService.updateUser(Long.MAX_VALUE,
                        new UpdateUserAccountDTO("不存在", null, "missing@example.com"))).getErrorCode());
    }

    @Test
    void userMappingAndRepeatedWritesPreserveDatabaseContract() {
        UserAccount account = new UserAccount();
        account.setId(1234567890123L);
        account.setUserNo("123456");
        account.setPhone("13800138000");
        account.setEmail("audit@example.com");
        assertEquals(1, userAccountMapper.insert(account));
        assertEquals(1234567890123L, account.getId());

        Long id = account.getId();
        UserAccount stored = userAccountMapper.selectById(id);
        assertEquals(0L, stored.getPasswordVersion());
        assertEquals(1, stored.getStatus());
        assertNull(stored.getPassword());
        assertNotNull(stored.getCreatedAt());
        assertNotNull(stored.getUpdatedAt());
        assertEquals(id, userAccountMapper.selectByPhone(account.getPhone()).getId());
        assertEquals(id, userAccountMapper.selectByEmail(account.getEmail()).getId());

        for (int attempt = 0; attempt < 2; attempt++) {
            assertEquals(1, userAccountMapper.updateProfile(id, "审查测试", "https://example.com/avatar.png"));
            assertEquals(1, userAccountMapper.updatePhone(id, account.getPhone()));
            assertEquals(1, userAccountMapper.updateEmail(id, account.getEmail()));
        }
        assertEquals(1, userAccountMapper.updatePassword(id, "test-password-hash"));
        assertEquals(1L, userAccountMapper.selectById(id).getPasswordVersion());
        assertEquals(1, userAccountMapper.updatePassword(id, "test-password-hash"));
        assertEquals(2L, userAccountMapper.selectById(id).getPasswordVersion());
        assertEquals(1L, userAccountMapper.countByCondition("123456", 1));
        UserAccount listed = userAccountMapper.selectPageByCondition("123456", 1, 0L, 10, null).getFirst();
        assertEquals(id, listed.getId());
        assertNull(listed.getPassword());
        assertNull(listed.getPasswordVersion());

        jdbcTemplate.update("DELETE FROM user_account WHERE id = ?", id);
        assertEquals(0, userAccountMapper.updateProfile(id, "审查测试", null));
        assertEquals(0, userAccountMapper.updatePassword(id, "test-password-hash"));
        assertEquals(0, userAccountMapper.updatePhone(id, account.getPhone()));
        assertEquals(0, userAccountMapper.updateEmail(id, account.getEmail()));
    }

    @Test
    void userCreatedAtSortingIsStableAcrossPages() {
        jdbcTemplate.update("""
                INSERT INTO user_account (id, user_no, nickname, status, created_at)
                VALUES (1007, 'sort-user-new', '普通排序新', 1, '2026-10-03 12:00:00'),
                       (1005, 'sort-user-old', '普通排序旧', 1, '2026-10-01 12:00:00'),
                       (1006, 'sort-user-tie', '普通排序同时间', 0, '2026-10-01 12:00:00')
                """);

        assertEquals(List.of("sort-user-old", "sort-user-tie", "sort-user-new"),
                userAccountMapper.selectPageByCondition("普通排序", null, 0L, 10, "asc")
                        .stream().map(UserAccount::getUserNo).toList());
        assertEquals(List.of("sort-user-new", "sort-user-tie", "sort-user-old"),
                userAccountMapper.selectPageByCondition("普通排序", null, 0L, 10, "desc")
                        .stream().map(UserAccount::getUserNo).toList());
        assertEquals(List.of("sort-user-new", "sort-user-tie", "sort-user-old"),
                userAccountMapper.selectPageByCondition("普通排序", null, 0L, 10, null)
                        .stream().map(UserAccount::getUserNo).toList());
        assertEquals("sort-user-tie", userAccountMapper.selectPageByCondition(
                "普通排序", null, 1L, 1, "asc").getFirst().getUserNo());
    }

    @Test
    void sysUserListExcludesPasswordWhileCredentialLookupRetainsIt() {
        SysUser credentials = sysUserMapper.selectByUsername("admin");
        assertNotNull(credentials.getPassword());
        assertEquals(credentials.getId(), sysUserMapper.selectByUsername("ADMIN").getId(),
                "Username lookup preserves the previous case-insensitive database behavior");
        assertEquals(1L, credentials.getId());
        assertEquals(0L, credentials.getAuthVersion());
        assertEquals(credentials.getId(), sysUserMapper.selectById(credentials.getId()).getId());
        assertEquals(1L, sysUserMapper.countByCondition("ADMIN", 1));
        SysUser listed = sysUserMapper.selectPageByCondition("admin", 1, 0L, 10, null).getFirst();
        assertEquals(credentials.getId(), listed.getId());
        assertNull(listed.getPassword());
    }

    @Test
    void sysUserCreatedAtSortingIsStableAcrossPages() {
        jdbcTemplate.update("""
                INSERT INTO sys_user (username, password, nickname, status, created_at)
                VALUES ('sort-new', 'test-hash', 'sort-test', 1, '2026-10-03 12:00:00'),
                       ('sort-old', 'test-hash', 'sort-test', 1, '2026-10-01 12:00:00'),
                       ('sort-tie', 'test-hash', 'sort-test', 0, '2026-10-01 12:00:00')
                """);

        assertEquals(List.of("sort-old", "sort-tie", "sort-new"),
                sysUserMapper.selectPageByCondition("sort-test", null, 0L, 10, "asc")
                        .stream().map(SysUser::getUsername).toList());
        assertEquals(List.of("sort-new", "sort-tie", "sort-old"),
                sysUserMapper.selectPageByCondition("sort-test", null, 0L, 10, "desc")
                        .stream().map(SysUser::getUsername).toList());
        assertEquals("sort-tie", sysUserMapper.selectPageByCondition("sort-test", null, 1L, 1, "asc")
                .getFirst().getUsername());
        assertEquals(List.of("sort-old", "sort-new"),
                sysUserMapper.selectPageByCondition("sort-test", 1, 0L, 10, "asc")
                        .stream().map(SysUser::getUsername).toList());
        assertEquals("sort-tie", sysUserMapper.selectPageByCondition("sort-test", null, 0L, 1, null)
                .getFirst().getUsername());
    }

    @Test
    void sysUserCrudWritesAndDeletesExpectedRows() {
        SysUser user = new SysUser();
        user.setUsername("crud-test-user");
        user.setPassword("initial-hash");
        user.setNickname("CRUD 测试");
        user.setStatus(1);

        assertEquals(1, sysUserMapper.insert(user));
        assertNotNull(user.getId());
        assertEquals(1, sysUserMapper.updateStatus(user.getId(), 0));
        assertEquals(1, sysUserMapper.update(user));
        assertEquals(1, sysUserMapper.updatePassword(user.getId(), "reset-hash"));
        assertEquals("reset-hash", sysUserMapper.selectById(user.getId()).getPassword());
        assertEquals(2L, sysUserMapper.selectById(user.getId()).getAuthVersion());
        assertEquals(1, sysUserMapper.deleteById(user.getId()));
        assertEquals(0, sysUserMapper.deleteById(user.getId()));
    }

    @Test
    void userListIncludesExistingAvatarColumn() {
        jdbcTemplate.update("""
                INSERT INTO user_account (id, user_no, avatar, nickname)
                VALUES (1008, 'avatar-list-test', 'https://files.example/avatar.png', '列表头像')
                """);
        var query = new com.web.project.user.dto.UserAccountQueryDTO();
        query.setKeyword("avatar-list-test");

        var result = userAccountService.getUserPage(query);

        assertEquals("https://files.example/avatar.png", result.records().getFirst().avatar());
    }

    @Test
    void systemAvatarReferencesFileIdAndLockedReadUsesFreshSchema() {
        jdbcTemplate.update("""
                INSERT INTO storage_file (provider, storage_key, content_type, size_bytes)
                VALUES ('local', 'sys-users/mapper-avatar.png', 'image/png', 7)
                """);
        Long fileId = jdbcTemplate.queryForObject(
                "SELECT id FROM storage_file WHERE storage_key = ?", Long.class, "sys-users/mapper-avatar.png");
        Long userId = sysUserMapper.selectByUsername("admin").getId();
        assertEquals(1, sysUserMapper.updateAvatar(userId, fileId));
        assertEquals(fileId, sysUserMapper.selectById(userId).getAvatarFileId());
        assertEquals(fileId, sysUserMapper.selectByIdForUpdate(userId).getAvatarFileId());
        assertNull(sysUserMapper.selectByIdForUpdate(userId).getPassword());
        assertEquals(1, sysUserMapper.updateAvatar(userId, null));
        assertNull(sysUserMapper.selectById(userId).getAvatarFileId());
    }

    @Test
    void repeatedChannelUpdateSucceedsAndInvalidConfigurationIsRejected() {
        for (int attempt = 0; attempt < 2; attempt++) {
            assertDoesNotThrow(() -> authChannelService.updateChannel(
                    AuthChannel.PHONE, new AuthChannelUpdateDTO(true, true)));
        }
        assertTrue(authChannelService.getChannels().phoneEnabled());
        assertEquals(1, sysConfigMapper.updateValue("auth.phone.enabled", "invalid"));
        assertThrows(IllegalStateException.class, authChannelService::getChannels);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void missingSecondConfigRowRollsBackFirstChannelWrite() {
        assertEquals(1, jdbcTemplate.update("DELETE FROM sys_config WHERE config_key = ?",
                "auth.phone.code.enabled"));
        try {
            assertThrows(IllegalStateException.class, () -> authChannelService.updateChannel(
                    AuthChannel.PHONE, new AuthChannelUpdateDTO(false, false)));
            assertEquals("true", jdbcTemplate.queryForObject(
                    "SELECT config_value FROM sys_config WHERE config_key = ?",
                    String.class, "auth.phone.enabled"));
        } finally {
            jdbcTemplate.update("INSERT INTO sys_config (config_key, config_value) VALUES (?, ?)",
                    "auth.phone.code.enabled", "true");
        }
    }
}
