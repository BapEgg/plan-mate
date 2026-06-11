package com.planmate.user.config;

import com.planmate.user.service.ProfileImageStorage;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ProfileImageResourceConfig implements WebMvcConfigurer {

    private final ProfileImageStorage profileImageStorage;

    public ProfileImageResourceConfig(ProfileImageStorage profileImageStorage) {
        this.profileImageStorage = profileImageStorage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(profileImageStorage.publicPathPattern())
                .addResourceLocations(profileImageStorage.uploadDirectory().toUri().toString());
    }

}
