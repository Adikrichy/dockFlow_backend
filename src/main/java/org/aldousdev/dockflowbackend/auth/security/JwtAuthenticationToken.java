package org.aldousdev.dockflowbackend.auth.security;

import org.aldousdev.dockflowbackend.auth.entity.User;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class JwtAuthenticationToken extends AbstractAuthenticationToken {
    private final User principal;
    private final String token;

    public JwtAuthenticationToken(User principal, String token, Collection<? extends GrantedAuthority> authorities){
        super(authorities);
        this.principal = principal;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials(){
        return null;
    }

    @Override
    public User getPrincipal(){
        return principal;
    }

    public String getToken(){
        return token;
    }
}
