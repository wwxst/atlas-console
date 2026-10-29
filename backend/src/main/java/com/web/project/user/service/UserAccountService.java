package com.web.project.user.service;

import com.web.project.common.result.PageResult;
import com.web.project.user.dto.UserAccountQueryDTO;
import com.web.project.user.dto.UpdateUserAccountDTO;
import com.web.project.user.vo.UserAccountListVO;

/**
 * 普通用户管理业务接口。
 */
public interface UserAccountService {

    /** 启停用指定普通用户。 */
    void updateStatus(Long id, Integer status);

    UserAccountListVO updateUser(Long id, UpdateUserAccountDTO updateDTO);

    /**
     * 分页查询普通用户列表。
     *
     * @param queryDTO 查询参数
     * @return 用户分页列表
     */
    PageResult<UserAccountListVO> getUserPage(
            UserAccountQueryDTO queryDTO
    );
}
