package com.web.project.message.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.common.result.PageResult;
import com.web.project.message.dto.SmsTemplateInputDTO;
import com.web.project.message.dto.SmsTemplateQueryDTO;
import com.web.project.message.entity.SmsTemplate;
import com.web.project.message.mapper.SmsTemplateMapper;
import com.web.project.message.vo.SmsTemplateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 短信模板资料维护。模板本身必须先在对应服务商控制台创建并审核。 */
@Service
@RequiredArgsConstructor
public class SmsTemplateService {

    private static final TypeReference<List<String>> VARIABLES_TYPE = new TypeReference<>() {};

    private final SmsTemplateMapper mapper;
    private final ObjectMapper objectMapper;

    public PageResult<SmsTemplateVO> page(SmsTemplateQueryDTO query) {
        String keyword = query.keyword() == null || query.keyword().isBlank()
                ? null : query.keyword().trim();
        int page = query.normalizedPage();
        int pageSize = query.normalizedPageSize();
        long total = mapper.count(keyword, query.provider(), query.kind(), query.reviewStatus());
        List<SmsTemplateVO> records = mapper.selectPage(
                        keyword, query.provider(), query.kind(), query.reviewStatus(),
                        (long) (page - 1) * pageSize, pageSize)
                .stream().map(this::toVO).toList();
        return new PageResult<>(total, page, pageSize, records);
    }

    @Transactional
    public SmsTemplateVO create(SmsTemplateInputDTO dto) {
        SmsTemplate template = fromInput(dto);
        try {
            requireWrite(mapper.insert(template));
        } catch (DuplicateKeyException exception) {
            throw duplicateTemplate(exception);
        }
        return pageById(template.getId());
    }

    @Transactional
    public SmsTemplateVO update(Long id, SmsTemplateInputDTO dto) {
        SmsTemplate template = fromInput(dto);
        template.setId(id);
        try {
            if (mapper.update(template) != 1) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "短信模板不存在");
            }
        } catch (DuplicateKeyException exception) {
            throw duplicateTemplate(exception);
        }
        return pageById(id);
    }

    @Transactional
    public void delete(Long id) {
        if (mapper.deleteById(id) != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "短信模板不存在");
        }
    }

    private SmsTemplateVO pageById(Long id) {
        SmsTemplate saved = mapper.selectById(id);
        if (saved == null) {
            throw new IllegalStateException("更新后的短信模板未找到");
        }
        return toVO(saved);
    }

    private SmsTemplate fromInput(SmsTemplateInputDTO dto) {
        SmsTemplate template = new SmsTemplate();
        template.setProvider(dto.provider());
        template.setName(dto.name().trim());
        template.setTemplateCode(dto.templateCode().trim());
        template.setKind(dto.kind());
        template.setContent(dto.content().trim());
        template.setVariablesJson(writeVariables(normalizeVariables(dto.variables())));
        template.setReviewStatus(dto.reviewStatus());
        template.setEnabled(dto.enabled());
        return template;
    }

    private SmsTemplateVO toVO(SmsTemplate template) {
        return new SmsTemplateVO(
                template.getId(), template.getProvider(), template.getName(),
                template.getTemplateCode(), template.getKind(), template.getContent(),
                readVariables(template.getVariablesJson()), template.getReviewStatus(),
                template.isEnabled(), template.getUpdatedAt());
    }

    private List<String> normalizeVariables(List<String> variables) {
        if (variables == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "短信模板变量不能为空");
        }
        List<String> normalized = variables.stream().map(String::trim).toList();
        if (normalized.stream().distinct().count() != normalized.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "短信模板变量不能重复");
        }
        return normalized;
    }

    private String writeVariables(List<String> variables) {
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("短信模板变量序列化失败", exception);
        }
    }

    private List<String> readVariables(String variablesJson) {
        try {
            return objectMapper.readValue(variablesJson, VARIABLES_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("短信模板变量数据损坏", exception);
        }
    }

    private BusinessException duplicateTemplate(DuplicateKeyException exception) {
        return new BusinessException(ErrorCode.BAD_REQUEST, "该短信服务商模板 ID 已登记", exception);
    }

    private void requireWrite(int rows) {
        if (rows != 1) {
            throw new IllegalStateException("短信模板未写入预期记录");
        }
    }
}
