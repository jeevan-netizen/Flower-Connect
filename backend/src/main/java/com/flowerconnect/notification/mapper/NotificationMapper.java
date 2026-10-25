package com.flowerconnect.notification.mapper;

import com.flowerconnect.notification.domain.Notification;
import com.flowerconnect.notification.dto.NotificationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * Maps a {@link Notification} to its read model (plan Phase 9, task 9.2).
 *
 * <p>Field names match between entity and DTO, so MapStruct copies them by
 * name with no explicit mappings.
 */
@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationMapper INSTANCE = Mappers.getMapper(NotificationMapper.class);

    NotificationResponse toResponse(Notification notification);
}
