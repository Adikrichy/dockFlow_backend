package org.aldousdev.dockflowbackend.auth.service.impls;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.dto.request.LoginRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.LoginResponse;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.enums.Status;
import org.aldousdev.dockflowbackend.auth.exceptions.UserNotActiveException;
import org.aldousdev.dockflowbackend.auth.repository.UserRepository;
import org.aldousdev.dockflowbackend.auth.security.JWTService;
import org.aldousdev.dockflowbackend.auth.mapper.AuthMapper;
import org.aldousdev.dockflowbackend.auth.service.AuthService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTService jwtService;
    private final AuthMapper authMapper;

    @Override
    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(()->new RuntimeException("User not found"));
        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())){
            throw new RuntimeException("Wrong password");
        }

        if(user.getStatus() == Status.ACTIVE) {


            String token = jwtService.generateToken(user);
            long expiresAt = System.currentTimeMillis() + 3600 * 1000;

            Cookie cookie = new Cookie("JWT", token);
            cookie.setHttpOnly(true);
            cookie.setSecure(true);
            cookie.setPath("/");
            cookie.setMaxAge(3600);
            response.addCookie(cookie);

            return authMapper.toLoginResponse(user);
        }

        else{
            throw new UserNotActiveException("User is locked");
        }
    }

    @Override
    public void logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("JWT", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    @Override
    public User getCurrentUser(){
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null || !authentication.isAuthenticated()){
            throw new RuntimeException("Authentication not found");
        }

        if(authentication.getPrincipal() instanceof User user){
            return user;
        }

        throw new RuntimeException("User is not active");

//        String email = authentication.getName();
//        return userRepository.findByEmail(email)
//                .orElseThrow(()->new RuntimeException("User not found"));
    }
}
