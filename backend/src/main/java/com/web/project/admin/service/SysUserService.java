package com.web.project.admin.service;

import com.web.project.admin.dto.CreateSysUserDTO;
import com.web.project.admin.dto.ResetSysUserPasswordDTO;
import com.web.project.admin.dto.SysUserQueryDTO;
import com.web.project.admin.dto.UpdateSysUserDTO;
import com.web.project.admin.vo.SysUserListVO;
import com.web.project.common.result.PageResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 系统用户管理业务接口。
 */
public interface SysUserService {

    /**
     * 分页查询系统用户列表。
     *
     * @param queryDTO 查询条件
     * @return 系统用户分页列表
     */
    PageResult<SysUserListVO> getSysUserPage(SysUserQueryDTO queryDTO);

    /**
     * 修改系统用户状态。
     */
    void updateStatus(Long id, Integer status);

    /**
     * 新增系统用户。
     */
    SysUserListVO create(CreateSysUserDTO createDTO);

    /**
     * 编辑系统用户基本资料和状态。
     */
    SysUserListVO update(Long id, UpdateSysUserDTO updateDTO);

    /**
     * 管理员重置系统用户密码。
     */
    void resetPassword(Long id, ResetSysUserPasswordDTO resetDTO);

    /**
     * 上传系统用户头像。
     */
    SysUserListVO updateAvatar(Long id, MultipartFile avatarFile);

    /**
     * 删除系统用户。
     */
    void delete(Long id);
}
