package com.flowerconnect.mapper;

import com.flowerconnect.domain.User;
import com.flowerconnect.security.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    @Mapping(target = "role", source = "user.role.name")
    UserResponse toResponse(User user);
}
