package com.flowerconnect.scheduler;

import com.flowerconnect.inventory.service.InventoryExpiryService;
import com.flowerconnect.inventory.service.InventoryExpiryService.SweepResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the expiry scheduler component (plan task 3.7).
 *
 * <p>The scheduler owns the cron and nothing else, so the only thing it can get wrong is
 * failing to trigger the sweep. It is deliberately not asserted here that the sweep
 * happens <em>on a schedule</em>: {@code @Scheduled} is registered by the container, not
 * by the bean, and the two sweeps' real behaviour against a database is covered in
 * {@code InventoryExpiryIntegrationTest} and the token cleanup tests.
 */
@ExtendWith(MockitoExtension.class)
class InventoryExpirySchedulerTest {

    @Mock
    private InventoryExpiryService inventoryExpiryService;

    @InjectMocks
    private InventoryExpiryScheduler scheduler;

    @Test
    void theScheduledRunDelegatesToTheSweep() {
        when(inventoryExpiryService.sweepExpiredStock()).thenReturn(new SweepResult(0, 0, 0));

        scheduler.sweepExpiredStock();

        verify(inventoryExpiryService).sweepExpiredStock();
        verifyNoMoreInteractions(inventoryExpiryService);
    }

    @Test
    void aSweepThatWroteSomethingStillReturnsNormally() {
        when(inventoryExpiryService.sweepExpiredStock()).thenReturn(new SweepResult(3, 2, 2));

        scheduler.sweepExpiredStock();

        verify(inventoryExpiryService).sweepExpiredStock();
    }
}
