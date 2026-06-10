package com.planmate.auth.dto;

public record GuidanceResponse(String message) {

    public static GuidanceResponse verificationSentIfPossible() {
        return new GuidanceResponse("입력한 정보가 유효하면 인증 메일을 발송합니다.");
    }

    public static GuidanceResponse recoverySentIfPossible() {
        return new GuidanceResponse("입력한 정보가 유효하면 계정 복구 메일을 발송합니다.");
    }

}
