package com.planmate.auth.service;

import com.planmate.auth.dto.SignupRequest;
import com.planmate.auth.dto.SignupResponse;
import com.planmate.auth.exception.DuplicateEmailException;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SignupService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        UserEntity user = UserEntity.create(
                email,
                passwordEncoder.encode(request.password()),
                request.nickname().trim()
        );

        return SignupResponse.from(userRepository.saveAndFlush(user).toAccount());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

}
