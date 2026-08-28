package com.planmate.recommendation.service;

import com.planmate.recommendation.api.Interest;
import com.planmate.recommendation.domain.CandidateSearchCategory;
import com.planmate.recommendation.domain.CandidateSearchQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CandidateSearchQueryFactory {

    public List<CandidateSearchQuery> create(
            String destinationName,
            Map<CandidateSearchCategory, Integer> weights,
            List<Interest> interests
    ) {
        List<CandidateSearchQuery> queries = new ArrayList<>();
        for (CandidateSearchCategory category : weights.keySet()) {
            queries.addAll(baseQueries(destinationName, category));
        }
        if (interests.contains(Interest.FOOD)) {
            queries.add(new CandidateSearchQuery(CandidateSearchCategory.MEAL, destinationName + " 현지 음식"));
            queries.add(new CandidateSearchQuery(CandidateSearchCategory.MEAL, "local cuisine in " + destinationName));
            queries.add(new CandidateSearchQuery(CandidateSearchCategory.MEAL, "traditional restaurants in " + destinationName));
        }
        if (interests.contains(Interest.SIGHTSEEING)) {
            queries.add(new CandidateSearchQuery(CandidateSearchCategory.CORE_VISIT, destinationName + " 필수 방문 명소"));
            queries.add(new CandidateSearchQuery(CandidateSearchCategory.CORE_VISIT, "must-see landmarks in " + destinationName));
        }
        return queries.stream().distinct().toList();
    }

    private List<CandidateSearchQuery> baseQueries(String destinationName, CandidateSearchCategory category) {
        return switch (category) {
            case MUST_VISIT -> List.of();
            case CORE_VISIT -> List.of(
                    new CandidateSearchQuery(category, destinationName + " 인기 관광지"),
                    new CandidateSearchQuery(category, "top tourist attractions in " + destinationName)
            );
            case MEAL -> List.of(
                    new CandidateSearchQuery(category, destinationName + " 인기 음식점"),
                    new CandidateSearchQuery(category, "popular restaurants in " + destinationName)
            );
            case CAFE -> List.of(
                    new CandidateSearchQuery(category, destinationName + " 카페"),
                    new CandidateSearchQuery(category, "best cafes in " + destinationName)
            );
            case CULTURE -> List.of(
                    new CandidateSearchQuery(category, destinationName + " 역사 문화 명소"),
                    new CandidateSearchQuery(category, "cultural attractions in " + destinationName)
            );
            case NATURE -> List.of(
                    new CandidateSearchQuery(category, destinationName + " 자연 명소"),
                    new CandidateSearchQuery(category, "nature spots in " + destinationName)
            );
            case SHOPPING -> List.of(
                    new CandidateSearchQuery(category, destinationName + " 쇼핑 거리"),
                    new CandidateSearchQuery(category, "shopping areas in " + destinationName)
            );
            case PHOTO -> List.of(
                    new CandidateSearchQuery(category, destinationName + " 사진 명소"),
                    new CandidateSearchQuery(category, "photo spots in " + destinationName)
            );
            case NIGHT_VIEW -> List.of(
                    new CandidateSearchQuery(category, destinationName + " 야경 명소"),
                    new CandidateSearchQuery(category, "night view spots in " + destinationName)
            );
            case ACTIVITY -> List.of(
                    new CandidateSearchQuery(category, destinationName + " 체험 액티비티"),
                    new CandidateSearchQuery(category, "activities in " + destinationName)
            );
            case REST -> List.of(
                    new CandidateSearchQuery(category, destinationName + " 휴식 산책"),
                    new CandidateSearchQuery(category, "relaxing places in " + destinationName)
            );
            case ART -> List.of(
                    new CandidateSearchQuery(category, destinationName + " 미술관 전시"),
                    new CandidateSearchQuery(category, "art museums galleries in " + destinationName)
            );
            case THEME_PARK -> List.of(
                    new CandidateSearchQuery(category, destinationName + " 테마파크"),
                    new CandidateSearchQuery(category, "theme parks in " + destinationName)
            );
            case LOCAL -> List.of(
                    new CandidateSearchQuery(category, destinationName + " 로컬 여행"),
                    new CandidateSearchQuery(category, "local hidden gems in " + destinationName)
            );
        };
    }
}
