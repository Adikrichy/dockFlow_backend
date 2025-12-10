package org.aldousdev.dockflowbackend.service;

import jakarta.servlet.http.HttpServletResponse;
import org.aldousdev.dockflowbackend.auth.dto.request.LoginRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.LoginResponse;
import org.aldousdev.dockflowbackend.auth.entity.User;

public interface AuthService {
    LoginResponse login(LoginRequest loginRequest, HttpServletResponse response);
    void logout(HttpServletResponse response);
    User getCurrentUser();
}
