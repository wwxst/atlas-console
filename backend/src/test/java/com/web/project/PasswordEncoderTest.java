package com.web.project;
import org.junit.jupiter.api.Test;
import com.web.project.config.PasswordEncoderConfig;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 密码加密测试。
 */
class PasswordEncoderTest {

    @Test
    void encodedPasswordAcceptsOriginalAndRejectsWrongPassword() {
        PasswordEncoder passwordEncoder =
                new PasswordEncoderConfig().passwordEncoder();

        String encodedPassword =
                passwordEncoder.encode("user12345678");

        assertNotEquals("user12345678", encodedPassword);
        assertTrue(passwordEncoder.matches("user12345678", encodedPassword));
        assertFalse(passwordEncoder.matches("wrong-password", encodedPassword));
    }
}
