package com.planmate.itinerary.route.kakao;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class KakaoDirectionsClientConfig {

    static final String BASE_URL = "https://apis-navi.kakaomobility.com";

    @Bean
    @Qualifier("kakaoDirectionsRestClient")
    RestClient kakaoDirectionsRestClient(
            RestClient.Builder builder,
            @Value("${app.kakao.directions.base-url:" + BASE_URL + "}") String baseUrl,
            @Value("${app.kakao.directions.connect-timeout:2s}") Duration connectTimeout,
            @Value("${app.kakao.directions.read-timeout:5s}") Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
