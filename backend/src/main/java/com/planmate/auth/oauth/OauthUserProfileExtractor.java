package com.planmate.auth.oauth;

import java.util.Map;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OauthUserProfileExtractor {

    public OauthUserProfile extract(OAuth2AuthenticationToken authentication) {
        OauthProvider provider = OauthProvider.fromRegistrationId(authentication.getAuthorizedClientRegistrationId());
        Map<String, Object> attributes = authentication.getPrincipal().getAttributes();

        return switch (provider) {
            case GOOGLE -> google(attributes);
            case KAKAO -> kakao(attributes);
            case NAVER -> naver(attributes);
        };
    }

    private OauthUserProfile google(Map<String, Object> attributes) {
        return requireEmail(new OauthUserProfile(
                OauthProvider.GOOGLE,
                requiredString(attributes, "sub"),
                string(attributes, "email"),
                bool(attributes, "email_verified"),
                string(attributes, "name")
        ));
    }

    private OauthUserProfile kakao(Map<String, Object> attributes) {
        Map<String, Object> kakaoAccount = map(attributes, "kakao_account");
        Map<String, Object> profile = map(kakaoAccount, "profile");

        return requireEmail(new OauthUserProfile(
                OauthProvider.KAKAO,
                requiredString(attributes, "id"),
                string(kakaoAccount, "email"),
                bool(kakaoAccount, "is_email_verified"),
                string(profile, "nickname")
        ));
    }

    private OauthUserProfile naver(Map<String, Object> attributes) {
        Map<String, Object> response = map(attributes, "response");

        return requireEmail(new OauthUserProfile(
                OauthProvider.NAVER,
                requiredString(response, "id"),
                string(response, "email"),
                true,
                string(response, "nickname")
        ));
    }

    private OauthUserProfile requireEmail(OauthUserProfile profile) {
        if (!StringUtils.hasText(profile.email())) {
            throw OauthProvider.oauth2Exception("OAuth2 provider did not return an email.");
        }
        return profile;
    }

    private String requiredString(Map<String, Object> attributes, String key) {
        String value = string(attributes, key);
        if (!StringUtils.hasText(value)) {
            throw OauthProvider.oauth2Exception("OAuth2 provider did not return " + key + ".");
        }
        return value;
    }

    private String string(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private boolean bool(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        if (value instanceof Map<?, ?> nested) {
            return (Map<String, Object>) nested;
        }
        return Map.of();
    }

}
