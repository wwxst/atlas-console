package com.web.project.admin.mapper;

import com.web.project.admin.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统用户数据库访问接口。
 */
@Mapper
public interface SysUserMapper {
    /**
     * 根据登录账号查询系统用户。
     */
    SysUser selectByUsername(@Param("username") String username);

    /**
     * 根据系统用户 ID 查询账号。
     */
    SysUser selectById(@Param("id") Long id);

    /**
     * 在头像引用更新或删除账号的短事务内读取并锁定当前账号。
     */
    SysUser selectByIdForUpdate(@Param("id") Long id);

    /**
     * 查询符合条件的系统用户总数。
     */
    long countByCondition(
            @Param("keyword") String keyword,
            @Param("status") Integer status
    );

    /**
     * 分页查询系统用户列表；不读取密码密文。
     *
     * @param keyword 账号或昵称的模糊搜索关键词
     * @param status 账号状态；null 表示不筛选
     * @param offset 从 0 开始的查询偏移量
     * @param pageSize 当前页数量
     * @param createdAtOrder 创建时间排序方向；null 表示按 ID 升序
     */
    List<SysUser> selectPageByCondition(
            @Param("keyword") String keyword,
            @Param("status") Integer status,
            @Param("offset") long offset,
            @Param("pageSize") int pageSize,
            @Param("createdAtOrder") String createdAtOrder
    );

    /**
     * 修改系统用户状态。
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 新增系统用户。
     */
    int insert(SysUser sysUser);

    /**
     * 编辑系统用户基本资料；状态由独立接口维护。
     */
    int update(SysUser sysUser);

    /**
     * 更新系统用户密码。
     */
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    /**
     * 更新系统用户头像。
     */
    int updateAvatar(@Param("id") Long id, @Param("avatarFileId") Long avatarFileId);

    /**
     * 删除系统用户。
     */
    int deleteById(@Param("id") Long id);
}
