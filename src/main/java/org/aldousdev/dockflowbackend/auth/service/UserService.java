package org.aldousdev.dockflowbackend.auth.service;

import org.aldousdev.dockflowbackend.auth.dto.request.RegisterRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.RegisterResponse;
import org.aldousdev.dockflowbackend.auth.entity.User;

public interface UserService {
    RegisterResponse register(RegisterRequest registerRequest);
    User getUserByEmail(String email);
    void resendVerificationCode(String email);
}
