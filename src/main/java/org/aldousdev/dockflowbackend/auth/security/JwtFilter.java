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
         String token = null;

         if (cookies != null) {
             token = Arrays.stream(cookies)
                     .filter(cookie -> "jwtWithCompany".equals(cookie.getName()))
                     .map(Cookie::getValue)
                     .findFirst()
                     .orElse(null);


             if (token == null) {
                 token = Arrays.stream(cookies)
                         .filter(cookie -> "JWT".equals(cookie.getName()))
                         .map(Cookie::getValue)
                         .findFirst()
                         .orElse(null);
             }
         }


         if( token != null && jwtService.isTokenValid(token)){
             String email = jwtService.extractEmail(token);

             if(SecurityContextHolder.getContext().getAuthentication() == null){
                 User user = userRepository.findByEmailWithMemberships(email)
                         .orElse(null);
                 if(user != null && jwtService.isTokenValid(token,user)){
                     JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                             user,
                             token,
                             user.getAuthorities()
                     );
//                     UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user,token,user.getAuthorities());
                     SecurityContextHolder.getContext().setAuthentication(authentication);
                 }
             }
         }



         filterChain.doFilter(request,response);
    }
}
