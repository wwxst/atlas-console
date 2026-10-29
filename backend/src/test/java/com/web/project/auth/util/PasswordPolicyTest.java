package com.web.project.auth.util;

import com.web.project.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    @Test
    void eightCharactersAreAccepted() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("12345678"));
    }

    @Test
    void seventyTwoCharactersAreAccepted() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("a".repeat(72)));
    }

    @Test
    void sevenCharactersAreRejected() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("1234567"));
    }

    @Test
    void seventyThreeCharactersAreRejected() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("a".repeat(73)));
    }

    @Test
    void multiBytePasswordOver72BytesIsRejected() {
        // 25 个中文字符长度为 25，但 UTF-8 编码为 75 字节
        String password = "密".repeat(25);
        assertEquals(25, password.length());
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate(password));
    }

    @Test
    void loginAcceptsExactByteLimitAndRejectsTrailingCharacters() {
        String password = "密".repeat(24);
        assertDoesNotThrow(() -> PasswordPolicy.validateForLogin(password));
        assertThrows(BusinessException.class, () -> PasswordPolicy.validateForLogin(password + "x"));
    }

    @Test
    void nullPasswordIsRejected() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate(null));
    }
}
