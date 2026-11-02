package com.web.project.message.controller;

import com.web.project.common.result.PageResult;
import com.web.project.common.result.Result;
import com.web.project.message.dto.SmsTemplateInputDTO;
import com.web.project.message.dto.SmsTemplateQueryDTO;
import com.web.project.message.service.SmsTemplateService;
import com.web.project.message.vo.SmsTemplateVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端短信模板登记接口。 */
@RestController
@RequestMapping("/api/sys-user/sms-templates")
@RequiredArgsConstructor
@Validated
public class SmsTemplateController {

    private final SmsTemplateService service;

    @GetMapping
    public Result<PageResult<SmsTemplateVO>> page(@Valid @ModelAttribute SmsTemplateQueryDTO query) {
        return Result.success(service.page(query));
    }

    @PostMapping
    public Result<SmsTemplateVO> create(@Valid @RequestBody SmsTemplateInputDTO dto) {
        return Result.success(service.create(dto));
    }

    @PutMapping("/{id}")
    public Result<SmsTemplateVO> update(
            @PathVariable @Positive(message = "短信模板 ID 不正确") Long id,
            @Valid @RequestBody SmsTemplateInputDTO dto) {
        return Result.success(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive(message = "短信模板 ID 不正确") Long id) {
        service.delete(id);
        return Result.success();
    }
}
