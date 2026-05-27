package com.example.hot6novelcraft.domain.user.entity;

import com.example.hot6novelcraft.domain.user.entity.enums.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UserDetailsImpl implements UserDetails, OAuth2User {

    private final User user;
    private final Map<String, Object> attributes;

    // 일반 로그인용 생성자
    public static UserDetailsImpl fromTemp(String email, UserRole role) {
        User tempUser = User.builder()
                .email(email)
                .role(role)
                .build();
        return new UserDetailsImpl(tempUser, null);
    }

    // 소셜 로그인용 생성자 - 구글에서 받은 정보 담김
    public UserDetailsImpl(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = (attributes == null) ? Collections.emptyMap() : Map.copyOf(attributes);
    }

    // Security 밖에서 User 객체 꺼냄
    public User getUser() {
        return user;
    }

    // Spring Security 권한으로 인식 후 출력
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(user.getRole().name()));
    }

    // 구글에서 받은 속성 맵핑
    @Override
    public Map<String, Object> getAttributes() {
        return attributes == null ? Collections.emptyMap() : attributes;
    }

    // OAuth2User의 식별자 = 이메일로 통일
    @Override
    public String getName() {
        return user.getEmail();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }
}
