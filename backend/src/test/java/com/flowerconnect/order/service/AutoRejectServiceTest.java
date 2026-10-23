package com.flowerconnect.order.service;

import com.flowerconnect.config.AppProperties;
import com.flowerconnect.config.PlatformConfig;
import com.flowerconnect.config.PlatformConfigRepository;
import com.flowerconnect.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AutoRejectService} configuration reading — the parts
 * not exercised by {@link com.flowerconnect.order.AutoRejectIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class AutoRejectServiceTest {

    @Mock
    private PlatformConfigRepository platformConfigRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStateMachine orderStateMachine;
    @Mock
    private AppProperties appProperties;
    @Mock
    private Clock clock;

    @InjectMocks
    private AutoRejectService autoRejectService;

    @Test
    void getAutoRejectMinutesReturnsConfiguredValueFromPlatformConfig() {
        when(platformConfigRepository.findByConfigKey("auto_reject_minutes"))
                .thenReturn(Optional.of(PlatformConfig.builder()
                        .configKey("auto_reject_minutes")
                        .configValue("60")
                        .build()));
        assertEquals(60, autoRejectService.getAutoRejectMinutes());
    }

    @Test
    void getAutoRejectMinutesFallsBackToDocumentedDefaultWhenRowMissing() {
        when(platformConfigRepository.findByConfigKey("auto_reject_minutes"))
                .thenReturn(Optional.empty());
        assertEquals(60, autoRejectService.getAutoRejectMinutes());
    }

    @Test
    void getAutoRejectMinutesParsesConfiguredValue() {
        when(platformConfigRepository.findByConfigKey("auto_reject_minutes"))
                .thenReturn(Optional.of(PlatformConfig.builder()
                        .configKey("auto_reject_minutes")
                        .configValue("30")
                        .build()));
        assertEquals(30, autoRejectService.getAutoRejectMinutes());
    }

    @Test
    void getAutoRejectMinutesFailsFastOnNonNumericValue() {
        when(platformConfigRepository.findByConfigKey("auto_reject_minutes"))
                .thenReturn(Optional.of(PlatformConfig.builder()
                        .configKey("auto_reject_minutes")
                        .configValue("not-a-number")
                        .build()));
        assertThrows(IllegalStateException.class, autoRejectService::getAutoRejectMinutes);
    }
}
