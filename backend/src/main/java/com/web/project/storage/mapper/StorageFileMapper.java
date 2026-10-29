package com.web.project.storage.mapper;

import com.web.project.storage.entity.StorageFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StorageFileMapper {
    StorageFile selectById(Long id);
    StorageFile selectByIdForUpdate(Long id);
    int insert(StorageFile file);
    int delete(Long id);
    long countBySpaceId(Long spaceId);
    long countAvatarReferences(@Param("fileId") Long fileId);
}
