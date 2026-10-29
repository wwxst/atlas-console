package com.web.project.common.exception;

import com.web.project.auth.controller.SysUserAuthController;
import com.web.project.admin.controller.SysUserController;
import com.web.project.admin.service.SysUserService;
import com.web.project.auth.service.SysUserAuthService;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.result.PageResult;
import com.web.project.user.controller.UserAccountController;
import com.web.project.user.service.UserAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private final UserAccountService users = mock(UserAccountService.class);
    private final SysUserAuthService auth = mock(SysUserAuthService.class);
    private final SysUserService sysUsers = mock(SysUserService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new UserAccountController(users), new SysUserAuthController(auth),
                        new SysUserController(sysUsers))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void invalidPageReturnsBadRequestWithoutCallingService() throws Exception {
        for (String page : List.of("not-a-number", "0", "")) {
            for (String path : List.of("/api/sys-user/users", "/api/sys-user/sys-users")) {
                mvc.perform(get(path).param("page", page))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
            }
        }
        verifyNoInteractions(users, sysUsers);
    }

    @Test
    void emptyPageSizeReturnsBadRequestWithoutCallingService() throws Exception {
        for (String path : List.of("/api/sys-user/users", "/api/sys-user/sys-users")) {
            mvc.perform(get(path).param("pageSize", ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000));
        }
        verifyNoInteractions(users, sysUsers);
    }

    @Test
    void invalidPathIdReturnsBadRequestWithoutCallingService() throws Exception {
        mvc.perform(delete("/api/sys-user/sys-users/not-a-number"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.msg").value(ErrorCode.BAD_REQUEST.getDefaultMessage()));
        verifyNoInteractions(sysUsers);
    }

    @Test
    void invalidCreatedAtOrderReturnsBadRequestWithoutCallingService() throws Exception {
        for (String path : List.of("/api/sys-user/sys-users", "/api/sys-user/users")) {
            for (String order : List.of("", "ASC", "invalid", "asc;DROP TABLE sys_user")) {
                mvc.perform(get(path).param("createdAtOrder", order))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(40000));
            }
        }
        verifyNoInteractions(users, sysUsers);
    }

    @Test
    void wrongMethodPreservesMethodNotAllowedAndAllowHeader() throws Exception {
        mvc.perform(delete("/api/sys-user/users"))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value(40000));
        verifyNoInteractions(users);
    }

    @Test
    void unsupportedContentTypeRemainsClientError() throws Exception {
        mvc.perform(post("/api/sys-user/auth/login").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(auth);
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/sys-user/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        verifyNoInteractions(auth);
    }

    @Test
    void validRequestAndBusinessErrorKeepTheirContracts() throws Exception {
        when(users.getUserPage(any())).thenReturn(new PageResult<>(0L, 1, 10, List.of()));
        mvc.perform(get("/api/sys-user/users"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records").isEmpty());
        when(auth.login(any())).thenThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        mvc.perform(post("/api/sys-user/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void unexpectedFailureRemainsServerErrorWithoutLeakingDetails() throws Exception {
        when(users.getUserPage(any())).thenThrow(new IllegalStateException("internal database details"));
        mvc.perform(get("/api/sys-user/users"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(50000))
                .andExpect(jsonPath("$.msg").value(ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage()));
    }

    @Test
    void exceptionMessagesAreNotWrittenToApplicationLogs() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(users.getUserPage(any())).thenThrow(
                    new IllegalStateException("password=secret-value, phone=13800138000",
                            new java.sql.SQLException("SQL parameters: refresh-token-value")));
            mvc.perform(get("/api/sys-user/users"))
                    .andExpect(status().isInternalServerError());

            assertEquals(1, appender.list.size());
            ILoggingEvent event = appender.list.getFirst();
            assertTrue(event.getFormattedMessage().contains("IllegalStateException"));
            assertTrue(event.getFormattedMessage().contains("SQLException"));
            assertTrue(event.getFormattedMessage().contains("GlobalExceptionHandlerTest"));
            assertFalse(event.getFormattedMessage().contains("secret-value"));
            assertFalse(event.getFormattedMessage().contains("13800138000"));
            assertFalse(event.getFormattedMessage().contains("refresh-token-value"));
            assertNull(event.getThrowableProxy());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void convertedServerFailureIsLoggedWithCauseAndSafeResponse() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(users.getUserPage(any())).thenThrow(new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR, new IllegalStateException("secret-database-value")));
            mvc.perform(get("/api/sys-user/users"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.msg").value(ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage()));

            assertEquals(1, appender.list.size());
            String diagnostic = appender.list.getFirst().getFormattedMessage();
            assertTrue(diagnostic.contains("BusinessException"));
            assertTrue(diagnostic.contains("IllegalStateException"));
            assertFalse(diagnostic.contains("secret-database-value"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
