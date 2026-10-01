package com.flowerconnect.mapper;

import com.flowerconnect.domain.User;
import com.flowerconnect.security.dto.AdminUserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * Maps a {@link User} to the administrator-facing representation (plan task 2.8).
 *
 * <p>Only the fields an admin needs are copied. Nothing on the entity — least of
 * all {@code passwordHash} — is mapped, so the admin user listing cannot leak a
 * credential. The bean-style {@code role} mapping mirrors {@link UserMapper},
 * whose only difference is the extra {@code status} field.
 */
@Mapper(componentModel = "spring")
public interface AdminUserMapper {

    AdminUserMapper INSTANCE = Mappers.getMapper(AdminUserMapper.class);

    @Mapping(target = "role", source = "user.role.name")
    AdminUserResponse toResponse(User user);
}