package com.web.project.deployment;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeploymentControllerTest {
    private final DeploymentService service = mock(DeploymentService.class);
    private final DeploymentController controller = new DeploymentController(service);

    private Jwt user(String accountType, String subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("signed-token")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("accountType", accountType)
                .build();
    }

    @Test
    void onlyRootSystemAdministratorCanRequestOrReadDeployment() {
        for (Jwt jwt : new Jwt[]{user("SYS_USER", "2"), user("USER", "1")}) {
            BusinessException denied = assertThrows(BusinessException.class,
                    () -> controller.deploy(jwt));
            assertEquals(ErrorCode.ACCESS_DENIED, denied.getErrorCode());
            assertThrows(BusinessException.class, () -> controller.status(jwt));
            assertThrows(BusinessException.class, () -> controller.recover(jwt));
            assertThrows(BusinessException.class, () -> controller.rollback(jwt));
            assertThrows(BusinessException.class, () -> controller.history(jwt));
            assertThrows(BusinessException.class, () -> controller.rollbackToHistory(
                    jwt, new DeploymentController.HistoryRollbackRequest("a".repeat(40) + "_" + "b".repeat(40))));
        }
        assertThrows(BusinessException.class, () -> controller.deploy(null));
        assertThrows(BusinessException.class, () -> controller.recover(null));
        assertThrows(BusinessException.class, () -> controller.rollback(null));
        assertThrows(BusinessException.class, () -> controller.history(null));
        verifyNoInteractions(service);

        when(service.status()).thenReturn(new DeploymentStatus(false, "not_configured", null, null, null, null));
        assertFalse(controller.status(user("SYS_USER", "1")).data().configured());
        verify(service).status();
    }
}
