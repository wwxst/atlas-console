package com.web.project.message.mapper;

import com.web.project.message.entity.SmsTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 短信模板数据访问。 */
@Mapper
public interface SmsTemplateMapper {

    SmsTemplate selectById(@Param("id") Long id);

    long count(
            @Param("keyword") String keyword,
            @Param("provider") String provider,
            @Param("kind") String kind,
            @Param("reviewStatus") String reviewStatus
    );

    List<SmsTemplate> selectPage(
            @Param("keyword") String keyword,
            @Param("provider") String provider,
            @Param("kind") String kind,
            @Param("reviewStatus") String reviewStatus,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SmsTemplate template);

    int update(SmsTemplate template);

    int deleteById(@Param("id") Long id);
}
