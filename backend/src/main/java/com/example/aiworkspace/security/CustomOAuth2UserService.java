package com.example.aiworkspace.security;
import com.example.aiworkspace.domain.user.User;
import com.example.aiworkspace.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        
        String provider = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();
        
        // Kakao attributes extraction
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        Map<String, Object> profile = kakaoAccount != null ? (Map<String, Object>) kakaoAccount.get("profile") : null;
        Map<String, Object> properties = (Map<String, Object>) attributes.get("properties");
        
        String providerId = String.valueOf(attributes.get("id"));
        String email = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
        
        String nickname = null;
        if (profile != null && profile.get("nickname") != null) {
            nickname = (String) profile.get("nickname");
        } else if (properties != null && properties.get("nickname") != null) {
            nickname = (String) properties.get("nickname");
        }
        
        if (nickname == null || nickname.isBlank()) {
            nickname = "카카오 사용자";
        }

        Optional<User> userOptional = userRepository.findByProviderAndProviderId(provider, providerId);
        User user;
        if (userOptional.isPresent()) {
            user = userOptional.get().update(nickname);
            user = userRepository.save(user);
        } else {
            user = User.builder()
                    .email(email != null ? email : providerId + "@kakao.com")
                    .nickname(nickname)
                    .provider(provider)
                    .providerId(providerId)
                    .role(com.example.aiworkspace.domain.user.Role.USER)
                    .build();
            user = userRepository.save(user);
        }
        
        return UserPrincipal.create(user, attributes);
    }
}
