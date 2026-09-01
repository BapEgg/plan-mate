package com.planmate.itinerary.route.kakao;

import com.planmate.itinerary.route.RouteTravelTimePort;
import com.planmate.itinerary.route.google.GoogleRoutesAdapter;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * spec §10.5/§15: 국내 자동차 route는 Kakao Mobility 자동차 길찾기로 결정됐지만, 그 provider
 * spike는 자동차 길찾기만 검증했다 — 도보/자전거는 대상이 아니었으므로 Google Routes를 그대로
 * 쓴다. {@link RouteTravelTimePort}의 유일한 활성 구현으로 {@code @Primary}를 붙여
 * {@link GoogleRoutesAdapter}(둘 다 같은 인터페이스를 구현) 대신 주입되게 한다 — Google 어댑터
 * 자체는 그대로 두고 이 라우터가 내부적으로 위임한다.
 */
@Service
@Primary
public class RouteTravelTimeRouter implements RouteTravelTimePort {

    private final KakaoDrivingRouteProvider kakaoDrivingRouteProvider;
    private final GoogleRoutesAdapter googleRoutesAdapter;

    public RouteTravelTimeRouter(
            KakaoDrivingRouteProvider kakaoDrivingRouteProvider,
            GoogleRoutesAdapter googleRoutesAdapter
    ) {
        this.kakaoDrivingRouteProvider = kakaoDrivingRouteProvider;
        this.googleRoutesAdapter = googleRoutesAdapter;
    }

    @Override
    public Optional<RouteTravelTime> findRoute(RoutePoint origin, RoutePoint destination, TravelMode travelMode) {
        if (travelMode == TravelMode.DRIVE) {
            return kakaoDrivingRouteProvider.findRoute(origin, destination);
        }
        return googleRoutesAdapter.findRoute(origin, destination, travelMode);
    }
}
