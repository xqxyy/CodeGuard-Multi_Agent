package com.codeguard.agent.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codeguard.agent.config.CodeGuardProperties;
import com.codeguard.agent.persistence.ReviewRepository;
import com.codeguard.agent.security.CurrentUserService;
import org.junit.jupiter.api.Test;

class ReviewTrafficGuardTest {

    @Test
    void rejectsWhenSubmitRateLimitIsExceeded() {
        ReviewRepository reviewRepository = mock(ReviewRepository.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        CodeGuardProperties properties = new CodeGuardProperties(
                new CodeGuardProperties.Review(200_000, 1, 20),
                null
        );
        ReviewTrafficGuard guard = new ReviewTrafficGuard(reviewRepository, properties, currentUserService);

        when(currentUserService.username()).thenReturn("alice");
        when(reviewRepository.countByOrganizationKeyAndStatusIn(eq("demo-enterprise"), anyCollection()))
                .thenReturn(0L);

        assertDoesNotThrow(() -> guard.assertCanSubmit("demo-enterprise"));
        assertThrows(RateLimitExceededException.class, () -> guard.assertCanSubmit("demo-enterprise"));
    }

    @Test
    void rejectsWhenOrganizationHasTooManyActiveReviews() {
        ReviewRepository reviewRepository = mock(ReviewRepository.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        CodeGuardProperties properties = new CodeGuardProperties(
                new CodeGuardProperties.Review(200_000, 30, 1),
                null
        );
        ReviewTrafficGuard guard = new ReviewTrafficGuard(reviewRepository, properties, currentUserService);

        when(currentUserService.username()).thenReturn("alice");
        when(reviewRepository.countByOrganizationKeyAndStatusIn(eq("demo-enterprise"), anyCollection()))
                .thenReturn(1L);

        assertThrows(RateLimitExceededException.class, () -> guard.assertCanSubmit("demo-enterprise"));
    }
}
