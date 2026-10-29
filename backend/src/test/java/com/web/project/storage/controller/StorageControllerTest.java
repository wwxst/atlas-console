package com.web.project.storage.controller;

import com.web.project.common.exception.GlobalExceptionHandler;
import com.web.project.storage.service.StorageSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StorageControllerTest {
    StorageSettingsService service = mock(StorageSettingsService.class);
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new StorageController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void credentialsExposeOnlyConfiguredBoolean() throws Exception {
        when(service.snapshot()).thenReturn(new StorageSettingsService.Snapshot("local", "private-id", "private-ciphertext", null));
        mvc.perform(get("/api/sys-user/storage/oss/credentials"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.accessKeyId").doesNotExist())
                .andExpect(jsonPath("$.data.accessKeySecret").doesNotExist());
    }

    @Test void storageSettingsUpdateReturnsOperationMessage() throws Exception {
        when(service.updateType("aliyun")).thenReturn(new StorageSettingsService.Snapshot("aliyun", "", "", null));
        mvc.perform(put("/api/sys-user/storage/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storageType\":\"aliyun\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("存储配置已保存"))
                .andExpect(jsonPath("$.data.storageType").value("aliyun"));
    }

    @Test void credentialsRequireBothAsciiFieldsAndRespectDatabaseCiphertextLimit() throws Exception {
        for (String body : List.of("{}", "{\"accessKeyId\":\"id\"}",
                "{\"accessKeyId\":\"id\",\"accessKeySecret\":\"\"}",
                "{\"accessKeyId\":\"id\",\"accessKeySecret\":\"中文\"}",
                "{\"accessKeyId\":\"id\",\"accessKeySecret\":\"" + "a".repeat(129) + "\"}")) {
            mvc.perform(put("/api/sys-user/storage/oss/credentials").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        }
        verifyNoInteractions(service);
        mvc.perform(put("/api/sys-user/storage/oss/credentials").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessKeyId\":\"id\",\"accessKeySecret\":\"" + "a".repeat(128) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.configured").value(true));
    }

    @Test void emptyOrInvalidPageDoesNotFallBackToDefaults() throws Exception {
        for (String page : List.of("", "0", "bad")) {
            mvc.perform(get("/api/sys-user/storage/spaces").param("page", page)).andExpect(status().isBadRequest());
        }
        for (String size : List.of("", "0", "101")) {
            mvc.perform(get("/api/sys-user/storage/spaces").param("pageSize", size)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void bindingRequiresSupportedAccessPermission() throws Exception {
        for (String field : List.of("", ",\"accessPermission\":null", ",\"accessPermission\":\"\"",
                ",\"accessPermission\":\"private\"", ",\"accessPermission\":\"public-read-write\"",
                ",\"accessPermission\":\"invalid\"")) {
            mvc.perform(post("/api/sys-user/storage/spaces").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"bucketName\":\"avatar-bucket\",\"regionId\":\"cn-hangzhou\"" + field + "}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void publicReadReachesBindingService() throws Exception {
        mvc.perform(post("/api/sys-user/storage/spaces").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bucketName\":\"avatar-bucket\",\"regionId\":\"cn-hangzhou\",\"accessPermission\":\"public-read\"}"))
                .andExpect(status().isOk());
        verify(service).bind(argThat(dto -> "public-read".equals(dto.accessPermission())));
    }
}
