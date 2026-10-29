package com.web.project.auth.util;

import com.web.project.auth.model.TargetType;
import com.web.project.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContactNormalizerTest {

    @Test
    void phoneIsTrimmedAndKept() {
        assertEquals("13800138000", ContactNormalizer.normalize(
                TargetType.PHONE, " 13800138000 "
        ));
    }

    @Test
    void invalidPhoneIsRejected() {
        for (String phone : new String[]{"12345", "23800138000", "1380013800a", ""}) {
            assertThrows(BusinessException.class, () -> ContactNormalizer.normalize(
                    TargetType.PHONE, phone
            ));
        }
    }

    @Test
    void emailIsTrimmedAndLowercased() {
        assertEquals("user@example.com", ContactNormalizer.normalize(
                TargetType.EMAIL, " User@Example.COM "
        ));
    }

    @Test
    void invalidEmailIsRejected() {
        for (String email : new String[]{"user", "user@", "@example.com", "user@example"}) {
            assertThrows(BusinessException.class, () -> ContactNormalizer.normalize(
                    TargetType.EMAIL, email
            ));
        }
    }
}
