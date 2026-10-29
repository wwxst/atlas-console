package com.web.project.user.controller;

import com.web.project.common.result.PageResult;
import com.web.project.common.result.Result;
import com.web.project.user.dto.UserAccountQueryDTO;
import com.web.project.user.dto.UpdateUserAccountDTO;
import com.web.project.user.dto.UserAccountStatusUpdateDTO;
import com.web.project.user.service.UserAccountService;
import com.web.project.user.vo.UserAccountListVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 后台普通用户管理接口。
 *
 * 接口属于管理端，只允许拥有 admin 权限的系统用户访问。
 */
@RestController
@RequestMapping("/api/sys-user/users")
@RequiredArgsConstructor
public class UserAccountController {

    private final UserAccountService userAccountService;

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserAccountStatusUpdateDTO updateDTO
    ) {
        userAccountService.updateStatus(id, updateDTO.status());
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<UserAccountListVO> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserAccountDTO updateDTO
    ) {
        return Result.success(userAccountService.updateUser(id, updateDTO));
    }

    /**
     * 分页查询普通用户列表。
     */
    @GetMapping
    public Result<PageResult<UserAccountListVO>> getUserPage(
            @Valid @ModelAttribute UserAccountQueryDTO queryDTO
    ) {
        PageResult<UserAccountListVO> pageResult =
                userAccountService.getUserPage(queryDTO);

        return Result.success(pageResult);
    }
}
