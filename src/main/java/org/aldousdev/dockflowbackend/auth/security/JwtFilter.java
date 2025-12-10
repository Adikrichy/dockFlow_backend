package org.aldousdev.dockflowbackend.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.repository.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final JWTService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
         Cookie[] cookies = request.getCookies();
         if (cookies != null){
             Arrays.stream(cookies)
                     .filter(cookie -> "JWT".equals(cookie.getName()))
                     .findFirst()
                     .ifPresent(cookie -> {
                         String token = cookie.getValue();
                         if(jwtService.isTokenValid(token)){
                             String email = jwtService.extractEmail(token);
                             if(SecurityContextHolder.getContext().getAuthentication() == null){
                                 User user = userRepository.findByEmail(email)
                                         .orElse(null);
                                 if(user != null && jwtService.isTokenValid(token,user)){
                                     UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities());
                                     SecurityContextHolder.getContext().setAuthentication(authentication);
                                 }
                             }
                         }
                     });
         }
         filterChain.doFilter(request,response);
    }
}
