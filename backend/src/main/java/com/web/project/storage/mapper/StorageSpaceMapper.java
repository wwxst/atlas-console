package com.web.project.storage.mapper;

import com.web.project.storage.entity.StorageSpace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface StorageSpaceMapper {
    StorageSpace selectById(Long id);
    StorageSpace selectByBucketName(String bucketName);
    List<StorageSpace> selectPage(@Param("offset") long offset, @Param("limit") int limit);
    long count();
    int insert(StorageSpace space);
    int updateDomain(@Param("id") Long id, @Param("accessDomain") String accessDomain);
    int updateRegion(@Param("id") Long id, @Param("regionId") String regionId);
    int delete(Long id);
}
