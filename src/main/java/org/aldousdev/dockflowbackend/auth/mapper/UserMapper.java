package org.aldousdev.dockflowbackend.auth.mapper;

import org.aldousdev.dockflowbackend.auth.dto.response.RegisterResponse;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "userId", source = "id")
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "status", expression = "java(user.getStatus().name())")
    RegisterResponse toRegisterResponse(User user);
}