package com.planmate.common.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.common.exception.ErrorCode;
import com.planmate.common.realtime.RealtimeEventType;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * C0: `docs/api/collaboration-workspace-api.md`가 코드와 어긋나지 않도록 고정하는 contract
 * test. 새 {@link ErrorCode} enum이나 {@link RealtimeEventType} 상수를 추가하면 이 test와
 * 문서를 함께 갱신해야 한다.
 */
class ErrorEventRegistryContractTest {

    private static final Set<String> DOCUMENTED_ERROR_CODES = Set.of(
            // CommonErrorCode
            "VALIDATION_ERROR",
            "FORBIDDEN",
            "DATA_CONFLICT",
            "RATE_LIMITED",
            // TripErrorCode
            "TRIP_NOT_FOUND",
            "INVALID_TRIP_REQUEST",
            // AuthErrorCode
            "DUPLICATE_LOGIN_ID",
            "DUPLICATE_EMAIL",
            "INVALID_CREDENTIALS",
            "EMAIL_NOT_VERIFIED",
            "INVALID_TOKEN",
            "EXPIRED_TOKEN",
            "TOKEN_ALREADY_USED",
            "EMAIL_SEND_FAILED",
            "REFRESH_TOKEN_STORE_UNAVAILABLE",
            // UserErrorCode
            "USER_NOT_FOUND",
            "INVALID_PROFILE_IMAGE",
            "PROFILE_IMAGE_SAVE_FAILED",
            // ItineraryErrorCode
            "GENERATION_NOT_FOUND",
            "GENERATION_NOT_READY",
            "GENERATION_INPUT_NOT_FOUND",
            "GENERATION_CANDIDATES_NOT_FOUND",
            "GENERATION_ALREADY_COMPLETED_WITH_DIFFERENT_DRAFT",
            "GENERATION_ITINERARY_STATE_INCONSISTENT",
            "GENERATION_TIME_WINDOW_INVALID",
            "NO_RECOMMENDATION_CANDIDATES",
            "UNSUPPORTED_PROMPT_VERSION",
            "INVALID_AI_RESPONSE",
            "AI_RESPONSE_VALIDATION_FAILED",
            "PLANNING_PROFILE_NOT_FOUND",
            "DESTINATION_NOT_RESOLVED",
            "ROUTE_PROVIDER_UNAVAILABLE",
            "ROUTE_PROVIDER_REQUEST_FAILED",
            "ROUTE_TRANSPORT_MODE_UNSUPPORTED",
            "GENERATION_CANDIDATE_LOCATION_INVALID",
            // MembershipErrorCode
            "INVALID_TRIP_TITLE",
            "TARGET_NOT_ACTIVE_MEMBER",
            "OWNER_CANNOT_LEAVE",
            "OWNER_TRANSFER_REQUEST_NOT_FOUND",
            "OWNER_TRANSFER_REQUEST_ALREADY_RESOLVED",
            "OWNER_TRANSFER_REQUEST_EXPIRED",
            "DUPLICATE_OWNER_TRANSFER_REQUEST",
            // InvitationErrorCode
            "INVITEE_NOT_FOUND",
            "INVITEE_ALREADY_ACTIVE_MEMBER",
            "DUPLICATE_PENDING_INVITATION",
            "TRIP_MEMBER_CAPACITY_EXCEEDED",
            "INVITATION_NOT_FOUND",
            "INVITATION_ALREADY_RESOLVED",
            "INVITATION_EXPIRED",
            // FriendErrorCode
            "ADDRESSEE_NOT_FOUND",
            "ALREADY_FRIENDS",
            "DUPLICATE_PENDING_FRIEND_REQUEST",
            "FRIEND_REQUEST_NOT_FOUND",
            "FRIEND_REQUEST_ALREADY_RESOLVED"
    );

    private static final Set<String> DOCUMENTED_REALTIME_EVENT_TYPES = Set.of(
            "ITINERARY_GENERATION_STATUS_CHANGED",
            "MEMBERSHIP_CHANGED",
            "INVITATION_RECEIVED",
            "CHAT_MESSAGE_SENT",
            "CHAT_UNREAD_CHANGED",
            "VOTE_OPENED",
            "VOTE_CLOSED",
            "ITINERARY_REVISION_APPLIED"
    );

    @Test
    void everyErrorCodeEnumConstantIsDocumented() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.planmate");
        Set<String> actualCodes = classes.stream()
                .filter(JavaClass::isEnum)
                .filter(javaClass -> javaClass.isAssignableTo(ErrorCode.class))
                .flatMap(javaClass -> codesOf(javaClass).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(actualCodes).isNotEmpty();
        assertThat(actualCodes).containsExactlyInAnyOrderElementsOf(DOCUMENTED_ERROR_CODES);
    }

    @Test
    void realtimeEventTypeRegistryMatchesDocumentedTypes() throws IllegalAccessException {
        Set<String> actualTypes = new LinkedHashSet<>();
        for (Field field : RealtimeEventType.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                actualTypes.add((String) field.get(null));
            }
        }

        assertThat(actualTypes).containsExactlyInAnyOrderElementsOf(DOCUMENTED_REALTIME_EVENT_TYPES);
    }

    private Set<String> codesOf(JavaClass enumClass) {
        try {
            Class<?> reflected = enumClass.reflect();
            Object[] constants = reflected.getEnumConstants();
            if (constants == null) {
                return Set.of();
            }
            return Arrays.stream(constants)
                    .map(constant -> ((ErrorCode) constant).code())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (NoClassDefFoundError error) {
            return Set.of();
        }
    }
}
