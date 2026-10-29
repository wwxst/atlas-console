package com.web.project.common.result;

import java.util.List;

/**
 * 分页查询结果。
 *
 * @param total    符合条件的数据总数
 * @param page     当前页码
 * @param pageSize 每页数量
 * @param records  当前页的数据列表
 * @param <T>      列表中每条数据的类型
 */
public record PageResult<T>(
        long total,
        int page,
        int pageSize,
        List<T> records
) {
}