package com.web.project.user.mapper;

import com.web.project.user.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 普通用户账号数据库操作接口。
 */
@Mapper
public interface UserAccountMapper {

    /**
     * 根据用户 ID 查询普通用户。
     */
    UserAccount selectById(@Param("id") Long id);

    /** 只更新账号状态，返回匹配行数。 */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 根据规范化手机号查询普通用户。
     */
    UserAccount selectByPhone(@Param("phone") String phone);

    /**
     * 根据规范化邮箱查询普通用户。
     */
    UserAccount selectByEmail(@Param("email") String email);

    /**
     * 创建普通用户账号，主键由调用方预先生成。
     */
    int insert(UserAccount userAccount);

    /**
     * 更新密码并递增密码版本。
     */
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    /**
     * 更新昵称和头像。
     */
    int updateProfile(
            @Param("id") Long id,
            @Param("nickname") String nickname,
            @Param("avatar") String avatar
    );

    /**
     * 更新手机号。
     */
    int updatePhone(@Param("id") Long id, @Param("phone") String phone);

    /**
     * 更新邮箱。
     */
    int updateEmail(@Param("id") Long id, @Param("email") String email);

    /**
     * 管理端仅更新昵称与联系方式，允许编辑禁用账号。
     */
    int updateAccount(
            @Param("id") Long id,
            @Param("nickname") String nickname,
            @Param("phone") String phone,
            @Param("email") String email
    );

    /**
     * 查询符合条件的用户总数。
     *
     * @param keyword 用户号码、手机号、邮箱或昵称关键词
     * @param status  用户状态
     * @return 用户数量
     */
    long countByCondition(
            @Param("keyword") String keyword,
            @Param("status") Integer status
    );

    /**
     * 分页查询普通用户。
     *
     * @param keyword  用户号码、手机号、邮箱或昵称关键词
     * @param status   用户状态
     * @param offset   查询起始位置
     * @param pageSize 每页数量
     * @param createdAtOrder 创建时间排序方向；null 表示按创建时间倒序
     * @return 当前页用户数据
     */
    List<UserAccount> selectPageByCondition(
            @Param("keyword") String keyword,
            @Param("status") Integer status,
            @Param("offset") long offset,
            @Param("pageSize") int pageSize,
            @Param("createdAtOrder") String createdAtOrder
    );
}
