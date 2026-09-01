package com.planmate.membership.service;

import com.planmate.membership.exception.MembershipErrorCode;
import com.planmate.membership.exception.MembershipException;
import java.text.BreakIterator;

/**
 * spec §5.1: "제목은... Unicode grapheme 기준 최대 30자, 줄바꿈·연속 공백은 한 공백으로 정규화한다."
 */
public final class TripTitleValidator {

    private static final int MAX_GRAPHEMES = 30;

    private TripTitleValidator() {
    }

    public static String normalizeAndValidate(String rawTitle) {
        if (rawTitle == null) {
            throw new MembershipException(MembershipErrorCode.INVALID_TRIP_TITLE, "여행방 제목을 입력해 주세요.");
        }
        String normalized = rawTitle.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw new MembershipException(MembershipErrorCode.INVALID_TRIP_TITLE, "여행방 제목을 입력해 주세요.");
        }
        if (graphemeCount(normalized) > MAX_GRAPHEMES) {
            throw new MembershipException(
                    MembershipErrorCode.INVALID_TRIP_TITLE,
                    "여행방 제목은 최대 " + MAX_GRAPHEMES + "자까지 입력할 수 있습니다."
            );
        }
        return normalized;
    }

    private static int graphemeCount(String value) {
        BreakIterator iterator = BreakIterator.getCharacterInstance();
        iterator.setText(value);
        int count = 0;
        while (iterator.next() != BreakIterator.DONE) {
            count++;
        }
        return count;
    }
}
