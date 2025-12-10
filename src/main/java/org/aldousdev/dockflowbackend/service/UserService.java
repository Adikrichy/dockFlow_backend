package org.aldousdev.dockflowbackend.service;

import org.aldousdev.dockflowbackend.auth.dto.request.RegisterRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.RegisterResponse;

public interface UserService {
    RegisterResponse register(RegisterRequest registerRequest);
}
