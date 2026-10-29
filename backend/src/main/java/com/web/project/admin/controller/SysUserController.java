package com.web.project.admin.controller;

import com.web.project.admin.dto.CreateSysUserDTO;
import com.web.project.admin.dto.ResetSysUserPasswordDTO;
import com.web.project.admin.dto.SysUserQueryDTO;
import com.web.project.admin.dto.SysUserStatusUpdateDTO;
import com.web.project.admin.dto.UpdateSysUserDTO;
import com.web.project.admin.service.SysUserService;
import com.web.project.admin.vo.SysUserListVO;
import com.web.project.common.result.PageResult;
import com.web.project.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

/**
 * 系统用户管理接口。
 */
@RestController
@RequestMapping("/api/sys-user/sys-users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    /**
     * 分页查询系统用户列表。
     *
     * @param queryDTO 查询条件
     * @return 系统用户分页列表
     */
    @GetMapping
    public Result<PageResult<SysUserListVO>> getSysUserPage(@Valid @ModelAttribute SysUserQueryDTO queryDTO) {
        PageResult<SysUserListVO> pageResult = sysUserService.getSysUserPage(queryDTO);
        return Result.success(pageResult);
    }

    /**
     * 启用或停用系统用户。
     */
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody SysUserStatusUpdateDTO updateDTO
    ) {
        sysUserService.updateStatus(id, updateDTO.status());
        return Result.success();
    }

    /**
     * 新增系统用户。
     */
    @PostMapping
    public Result<SysUserListVO> create(@Valid @RequestBody CreateSysUserDTO createDTO) {
        return Result.success(sysUserService.create(createDTO));
    }

    /**
     * 编辑系统用户基本资料和状态。
     */
    @PutMapping("/{id}")
    public Result<SysUserListVO> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSysUserDTO updateDTO
    ) {
        return Result.success(sysUserService.update(id, updateDTO));
    }

    /**
     * 管理员重置系统用户密码。
     */
    @PatchMapping("/{id}/password")
    public Result<Void> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetSysUserPasswordDTO resetDTO
    ) {
        sysUserService.resetPassword(id, resetDTO);
        return Result.success();
    }

    /**
     * 上传系统用户头像。
     */
    @PostMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<SysUserListVO> updateAvatar(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file
    ) {
        return Result.success(sysUserService.updateAvatar(id, file));
    }

    /**
     * 删除系统用户。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.delete(id);
        return Result.success();
    }
}
