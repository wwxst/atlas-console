package com.web.project.auth.session;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthSessionDataTest {
    @Test
    void existingOrdinaryUserSessionJsonRetainsPasswordVersion() {
        AuthSessionData session = JsonMapper.builder().build().readValue(
                "{\"accountId\":42,\"passwordVersion\":3}", AuthSessionData.class);

        assertEquals(42L, session.accountId());
        assertEquals(3L, session.passwordVersion());
        assertNull(session.authVersion());
    }
}
