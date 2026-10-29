package com.web.project.storage.mapper;

import com.web.project.channel.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface StorageConfigMapper {
    List<SysConfig> selectStorageConfig();
    List<SysConfig> selectStorageConfigForUpdate();
    int updateValue(@Param("key") String key, @Param("value") String value);
}
