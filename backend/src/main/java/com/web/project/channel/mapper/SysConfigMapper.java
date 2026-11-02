package com.web.project.channel.mapper;

import com.web.project.channel.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统配置数据库操作接口。
 */
@Mapper
public interface SysConfigMapper {

    /**
     * 按配置键查询配置项。
     *
     * @param keys 配置键集合
     * @return 匹配的配置项，按配置键升序
     */
    List<SysConfig> selectByKeys(@Param("keys") List<String> keys);

    /** 按固定键顺序锁定配置项，供同一事务内的敏感配置更新使用。 */
    List<SysConfig> selectByKeysForUpdate(@Param("keys") List<String> keys);

    /**
     * 锁定手机号和邮箱两个渠道总开关，供渠道配置更新事务使用。
     *
     * 两个渠道更新共用这组行锁；PostgreSQL 按查询中的 CASE 排序稳定获取行锁。
     *
     * @return 手机号、邮箱两个总开关配置项
     */
    List<SysConfig> selectMasterSwitchesForUpdate();

    /**
     * 更新单个配置项的值。
     *
     * @param key   配置键
     * @param value 配置值
     * @return 受影响行数
     */
    int updateValue(@Param("key") String key, @Param("value") String value);
}
