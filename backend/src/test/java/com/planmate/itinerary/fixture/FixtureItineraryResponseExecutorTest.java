package com.planmate.itinerary.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.service.ManualItineraryResponseService;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class FixtureItineraryResponseExecutorTest {

    @Test
    void delegatesToTheExistingProviderResponsePath() {
        ManualItineraryResponseService responseService = Mockito.mock(ManualItineraryResponseService.class);
        FixtureItineraryResponseExecutor executor = new FixtureItineraryResponseExecutor(responseService);
        AiItineraryDraft draft = new AiItineraryDraft(
                "1360",
                List.of(new ItineraryDraftDay(
                        1,
                        List.of(new ItineraryDraftItem(1, "place-1", "09:00", 60))
                ))
        );

        executor.submit(1484L, 1360L, draft);

        verify(responseService).submitProviderResponse(1484L, 1360L, draft);
    }

    @Test
    void startsASeparateTransactionAfterTheReadyEventCommit() throws NoSuchMethodException {
        Method method = FixtureItineraryResponseExecutor.class.getMethod(
                "submit",
                Long.class,
                Long.class,
                AiItineraryDraft.class
        );
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
