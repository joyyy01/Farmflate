package com.example.aiworkspace.security;
import com.example.aiworkspace.domain.user.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Getter
public class UserPrincipal implements OAuth2User {
    private Long id;
    private String email;
    private Collection<? extends GrantedAuthority> authorities;
    private Map<String, Object> attributes;

    public UserPrincipal(Long id, String email, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.authorities = authorities;
    }

    public static UserPrincipal create(User user, Map<String, Object> attributes) {
        UserPrincipal userPrincipal = new UserPrincipal(
                user.getId(),
                user.getEmail(),
                Collections.singletonList(new SimpleGrantedAuthority(user.getRoleKey()))
        );
        userPrincipal.attributes = attributes;
        return userPrincipal;
    }

    @Override
    public Map<String, Object> getAttributes() { return attributes; }
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override
    public String getName() { return String.valueOf(id); }
}
