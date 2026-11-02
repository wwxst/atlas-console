package com.web.project.message.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.project.common.exception.BusinessException;
import com.web.project.message.dto.SmsTemplateInputDTO;
import com.web.project.message.dto.SmsTemplateQueryDTO;
import com.web.project.message.entity.SmsTemplate;
import com.web.project.message.mapper.SmsTemplateMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SmsTemplateServiceTest {

    private final SmsTemplateMapper mapper = mock(SmsTemplateMapper.class);
    private final SmsTemplateService service = new SmsTemplateService(mapper, new ObjectMapper());

    @Test
    void createAndPagePreserveTemplateVariablesAndFilters() {
        when(mapper.insert(any())).thenAnswer(invocation -> {
            SmsTemplate saved = invocation.getArgument(0);
            saved.setId(12L);
            saved.setUpdatedAt(LocalDateTime.parse("2026-11-02T10:00:00"));
            return 1;
        });
        SmsTemplate createdRecord = template();
        createdRecord.setId(12L);
        createdRecord.setUpdatedAt(LocalDateTime.parse("2026-11-02T10:00:00"));
        when(mapper.selectById(12L)).thenReturn(createdRecord);
        when(mapper.count("login", "aliyun", "verification", "approved")).thenReturn(1L);
        SmsTemplate saved = template();
        saved.setId(12L);
        saved.setUpdatedAt(LocalDateTime.parse("2026-11-02T10:00:00"));
        when(mapper.selectPage("login", "aliyun", "verification", "approved", 0L, 20))
                .thenReturn(List.of(saved));

        var created = service.create(input());
        var page = service.page(new SmsTemplateQueryDTO(1, 20, " login ", "aliyun", "verification", "approved"));

        assertEquals(12L, created.id());
        assertEquals(List.of("code", "order_id"), created.variables());
        assertEquals(1L, page.total());
        assertEquals(List.of("code", "order_id"), page.records().getFirst().variables());
        verify(mapper).selectPage("login", "aliyun", "verification", "approved", 0L, 20);
    }

    @Test
    void updateReadsBackPersistedTemplate() {
        when(mapper.update(any())).thenReturn(1);
        SmsTemplate saved = template();
        saved.setId(12L);
        saved.setUpdatedAt(LocalDateTime.parse("2026-11-02T10:00:00"));
        when(mapper.selectById(12L)).thenReturn(saved);

        var result = service.update(12L, input());

        assertEquals("login", result.name());
        assertEquals(List.of("code", "order_id"), result.variables());
    }

    @Test
    void duplicateProviderTemplateIdIsBusinessError() {
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
        BusinessException failure = assertThrows(BusinessException.class, () -> service.create(input()));
        assertTrue(failure.getMessage().contains("模板 ID"));
        assertNotNull(failure.getCause());
    }

    @Test
    void duplicateTemplateVariablesAreRejectedBeforeDatabaseWrite() {
        SmsTemplateInputDTO duplicateVariables = new SmsTemplateInputDTO(
                "aliyun", "login", "SMS_100", "verification",
                "验证码：${code}", List.of("code", "code"), "approved", true);

        assertThrows(BusinessException.class, () -> service.create(duplicateVariables));
        verify(mapper, never()).insert(any());
    }

    @Test
    void updateAndDeleteRejectMissingTemplate() {
        when(mapper.update(any())).thenReturn(0);
        when(mapper.deleteById(99L)).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.update(99L, input()));
        assertThrows(BusinessException.class, () -> service.delete(99L));
    }

    private SmsTemplateInputDTO input() {
        return new SmsTemplateInputDTO("aliyun", " login ", "SMS_100", "verification",
                "验证码：${code}", List.of("code", "order_id"), "approved", true);
    }

    private SmsTemplate template() {
        SmsTemplate template = new SmsTemplate();
        template.setProvider("aliyun");
        template.setName("login");
        template.setTemplateCode("SMS_100");
        template.setKind("verification");
        template.setContent("验证码：${code}");
        template.setVariablesJson("[\"code\",\"order_id\"]");
        template.setReviewStatus("approved");
        template.setEnabled(true);
        return template;
    }
}
