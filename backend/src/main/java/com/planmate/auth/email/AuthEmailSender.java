package com.planmate.auth.email;

import com.planmate.user.entity.UserEntity;

public interface AuthEmailSender {

    void sendSignupVerification(UserEntity user, String rawToken);

}
