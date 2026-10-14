package com.eventbook.backend.users;

import com.eventbook.backend.users.dto.UserResponse;
import org.mapstruct.Mapper;


@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}