package com.web.project.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 后台用户列表查询参数。
 */
@Getter
@Setter
public class UserAccountQueryDTO {

    /**
     * 当前页码，默认第 1 页。
     */
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;

    /**
     * 每页数量，默认 10 条，最多查询 100 条。
     */
    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer pageSize = 10;

    /**
     * 用户号码、手机号、邮箱或昵称的模糊搜索关键词。
     */
    @Size(max = 50, message = "搜索关键词不能超过50个字符")
    private String keyword;

    /**
     * 用户状态：
     * 0 表示禁用；
     * 1 表示正常；
     * 不传表示查询全部状态。
     */
    @Min(value = 0, message = "用户状态不正确")
    @Max(value = 1, message = "用户状态不正确")
    private Integer status;

    /**
     * 创建时间排序方向；不传时按创建时间倒序。
     */
    @Pattern(regexp = "asc|desc", message = "创建时间排序方向只能为asc或desc")
    private String createdAtOrder;
}
