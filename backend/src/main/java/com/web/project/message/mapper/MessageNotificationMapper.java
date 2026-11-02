package com.web.project.message.mapper;

import com.web.project.message.entity.MessageNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 业务通知场景配置数据访问。 */
@Mapper
public interface MessageNotificationMapper {

    MessageNotification selectById(@Param("id") Long id);

    List<MessageNotification> selectByCategory(@Param("category") String category);

    int insert(MessageNotification notification);

    int updateConfiguration(MessageNotification notification);

    int deleteCustomById(@Param("id") Long id);

    int countEnabledBySmsTemplateId(@Param("templateId") Long templateId);

    int countEnabledUsingOtherProvider(@Param("provider") String provider);
}
