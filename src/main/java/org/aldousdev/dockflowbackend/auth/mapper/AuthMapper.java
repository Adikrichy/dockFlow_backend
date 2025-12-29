package org.aldousdev.dockflowbackend.auth.mapper;

import org.aldousdev.dockflowbackend.auth.dto.response.LoginResponse;
import org.aldousdev.dockflowbackend.auth.dto.response.UserResponse;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthMapper {
    @Mapping(target = "userId", source = "id")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "userType", source = "userType")
    @Mapping(target = "message", expression = "java(\"Login successful\")")
    @Mapping(target = "expiresAt", expression = "java(System.currentTimeMillis() + 3600_000)")
    @Mapping(target = "user", source = ".")
    LoginResponse toLoginResponse(User user);

    UserResponse toUserResponse(User user);
}
