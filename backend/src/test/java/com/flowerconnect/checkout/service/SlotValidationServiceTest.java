package com.flowerconnect.checkout.service;

import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.VendorHours;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.repository.VendorHoursRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotValidationServiceTest {

    @Mock
    private VendorHoursRepository vendorHoursRepository;

    private Clock clock;
    private SlotValidationService service;
    private VendorProfile vendor;

    // Anchor time: Wednesday, October 21, 2026, 04:00:00 UTC = 09:30:00 IST
    private static final Instant FIXED_INSTANT = Instant.parse("2026-10-21T04:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        service = new SlotValidationService(clock, vendorHoursRepository);
        ReflectionTestUtils.setField(service, "minLeadTimeMinutes", 120);
        ReflectionTestUtils.setField(service, "maxBookingHorizonDays", 7);

        ServiceLocation serviceLocation = ServiceLocation.builder()
                .id(1L)
                .city("Bengaluru")
                .area("Koramangala")
                .timezone("Asia/Kolkata")
                .build();

        vendor = VendorProfile.builder()
                .id(10L)
                .businessName("Test Florist")
                .status(VendorProfile.Status.APPROVED)
                .acceptingOrders(true)
                .serviceLocation(serviceLocation)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(5)
                .build();
    }

    @Nested
    @DisplayName("parseAndNormalize: Accepted Formats")
    class AcceptedFormatsTests {

        @Test
        @DisplayName("Accepts canonical UTC local timestamp without offset")
        void acceptsCanonicalUtcLocal() {
            LocalDateTime result = service.parseAndNormalize("2026-10-22T03:30:00");
            assertThat(result).isEqualTo(LocalDateTime.of(2026, 10, 22, 3, 30, 0));
        }

        @Test
        @DisplayName("Accepts canonical UTC local timestamp with minute precision")
        void acceptsCanonicalUtcMinutePrecision() {
            LocalDateTime result = service.parseAndNormalize("2026-10-22T03:30");
            assertThat(result).isEqualTo(LocalDateTime.of(2026, 10, 22, 3, 30, 0));
        }

        @Test
        @DisplayName("Accepts explicit Zulu UTC timestamp")
        void acceptsZuluUtc() {
            LocalDateTime result = service.parseAndNormalize("2026-10-22T03:30:00Z");
            assertThat(result).isEqualTo(LocalDateTime.of(2026, 10, 22, 3, 30, 0));
        }

        @Test
        @DisplayName("Accepts explicit Zulu UTC timestamp with minute precision")
        void acceptsZuluUtcMinutePrecision() {
            LocalDateTime result = service.parseAndNormalize("2026-10-22T03:30Z");
            assertThat(result).isEqualTo(LocalDateTime.of(2026, 10, 22, 3, 30, 0));
        }

        @Test
        @DisplayName("Accepts explicit offset timestamp and normalizes to UTC")
        void acceptsExplicitOffsetAndNormalizesToUtc() {
            // 09:00:00+05:30 in Asia/Kolkata -> 03:30:00 UTC
            LocalDateTime result = service.parseAndNormalize("2026-10-22T09:00:00+05:30");
            assertThat(result).isEqualTo(LocalDateTime.of(2026, 10, 22, 3, 30, 0));
        }

        @Test
        @DisplayName("Accepts explicit offset timestamp with minute precision")
        void acceptsExplicitOffsetMinutePrecision() {
            // 09:00+05:30 -> 03:30:00 UTC
            LocalDateTime result = service.parseAndNormalize("2026-10-22T09:00+05:30");
            assertThat(result).isEqualTo(LocalDateTime.of(2026, 10, 22, 3, 30, 0));
        }
    }

    @Nested
    @DisplayName("parseAndNormalize: Rejections")
    class RejectionsTests {

        @ParameterizedTest(name = "Rejects invalid string: \"{0}\"")
        @ValueSource(strings = {
                "",
                "   ",
                "2026-10-22",                          // date-only
                "2026-10-22T03:30:00.000Z",             // fractional seconds
                "2026-10-22T03:30:00.000",              // fractional seconds local
                "2026-10-22T09:00:00+05",               // malformed offset missing minutes
                "2026-10-22T09:00:00+0530",             // malformed offset missing colon
                "2026-10-22T09:00:00+05:3",             // malformed offset incomplete
                "2026-02-31T10:00:00",                  // invalid calendar date
                "2026-10-22T25:00:00",                  // invalid hour
                "2026-10-22T10:65:00",                  // invalid minute
                "2026-10-22T10:00:65",                  // invalid second
                "2026-10-22T03:30:00junk",              // trailing characters
                "2026-10-22T03:30:00Zjunk",             // trailing characters after Z
                "random-string"
        })
        void rejectsMalformedStrings(String input) {
            assertThatThrownBy(() -> service.parseAndNormalize(input))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                    .hasMessage("Invalid slotStart format (expected ISO-8601)");
        }

        @Test
        @DisplayName("Rejects null string")
        void rejectsNullString() {
            assertThatThrownBy(() -> service.parseAndNormalize(null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                    .hasMessage("Invalid slotStart format (expected ISO-8601)");
        }
    }

    @Nested
    @DisplayName("validateSlot: Business & Boundary Rules")
    class BusinessRulesTests {

        @Test
        @DisplayName("Requires positive slot duration")
        void requiresPositiveSlotDuration() {
            vendor.setSlotDurationMinutes(0);
            assertThatThrownBy(() -> service.validateSlot(vendor, "2026-10-21T06:30:00Z"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                    .hasMessage("Vendor slot duration configuration is invalid");

            vendor.setSlotDurationMinutes(-15);
            assertThatThrownBy(() -> service.validateSlot(vendor, "2026-10-21T06:30:00Z"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                    .hasMessage("Vendor slot duration configuration is invalid");
        }

        @Test
        @DisplayName("Rejects slot strictly in the past")
        void rejectsSlotInThePast() {
            // Anchor is Wednesday 09:30 IST (04:00 UTC). Slot at 08:30 IST (03:00 UTC) is strictly past.
            assertThatThrownBy(() -> service.validateSlot(vendor, "2026-10-21T03:00:00Z"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                    .hasMessage("Selected slot is in the past");
        }

        @Test
        @DisplayName("Enforces 120-minute lead time: exact 120 minutes is valid, 119 minutes is rejected")
        void enforces120MinuteLeadTimeBoundary() {
            // Vendor has 30m slot duration to test 11:30 IST on-grid
            vendor.setSlotDurationMinutes(30);
            VendorHours hours = VendorHours.builder()
                    .vendorProfile(vendor)
                    .weekday(DayOfWeek.WEDNESDAY)
                    .openTime(LocalTime.of(9, 0))
                    .closeTime(LocalTime.of(18, 0))
                    .closed(false)
                    .build();
            when(vendorHoursRepository.findByVendorProfileIdAndWeekday(eq(10L), eq(DayOfWeek.WEDNESDAY)))
                    .thenReturn(Optional.of(hours));

            // Anchor is 09:30:00 IST (04:00:00 UTC)
            // 119 minutes ahead: 11:29:00 IST = 05:59:00 UTC -> rejected by lead time
            assertThatThrownBy(() -> service.validateSlot(vendor, "2026-10-21T05:59:00Z"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                    .hasMessage("Selected slot violates minimum lead time");

            // Exact 120 minutes ahead: 11:30:00 IST = 06:00:00 UTC -> exactly on boundary -> valid!
            SlotValidationService.ValidatedSlot validated = service.validateSlot(vendor, "2026-10-21T06:00:00Z");
            assertThat(validated.slotStartUtc()).isEqualTo(LocalDateTime.of(2026, 10, 21, 6, 0, 0));
            assertThat(validated.slotEndUtc()).isEqualTo(LocalDateTime.of(2026, 10, 21, 6, 30, 0));
        }

        @Test
        @DisplayName("Enforces 7-day booking horizon: day 7 is valid, day 8 is rejected")
        void enforces7DayBookingHorizon() {
            // Anchor is Wednesday 2026-10-21.
            // Day 7 is Wednesday 2026-10-28 (inclusive horizon).
            // Day 8 is Thursday 2026-10-29.
            VendorHours hoursWed = VendorHours.builder()
                    .vendorProfile(vendor)
                    .weekday(DayOfWeek.WEDNESDAY)
                    .openTime(LocalTime.of(9, 0))
                    .closeTime(LocalTime.of(18, 0))
                    .closed(false)
                    .build();
            when(vendorHoursRepository.findByVendorProfileIdAndWeekday(eq(10L), eq(DayOfWeek.WEDNESDAY)))
                    .thenReturn(Optional.of(hoursWed));

            // Day 7: 2026-10-28 12:00 IST = 06:30 UTC -> valid
            SlotValidationService.ValidatedSlot validated = service.validateSlot(vendor, "2026-10-28T06:30:00Z");
            assertThat(validated.slotStartUtc()).isEqualTo(LocalDateTime.of(2026, 10, 28, 6, 30, 0));

            // Day 8: 2026-10-29 12:00 IST = 06:30 UTC -> rejected
            assertThatThrownBy(() -> service.validateSlot(vendor, "2026-10-29T06:30:00Z"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                    .hasMessage("Date is beyond the booking horizon");
        }

        @Test
        @DisplayName("Rejects closed vendor day")
        void rejectsClosedVendorDay() {
            VendorHours closedHours = VendorHours.builder()
                    .vendorProfile(vendor)
                    .weekday(DayOfWeek.WEDNESDAY)
                    .closed(true)
                    .build();
            when(vendorHoursRepository.findByVendorProfileIdAndWeekday(eq(10L), eq(DayOfWeek.WEDNESDAY)))
                    .thenReturn(Optional.of(closedHours));

            assertThatThrownBy(() -> service.validateSlot(vendor, "2026-10-21T06:30:00Z"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                    .hasMessage("Vendor is closed on WEDNESDAY");
        }

        @Test
        @DisplayName("Rejects slot starting before opening time")
        void rejectsSlotBeforeOpeningTime() {
            VendorHours hours = VendorHours.builder()
                    .vendorProfile(vendor)
                    .weekday(DayOfWeek.THURSDAY)
                    .openTime(LocalTime.of(9, 0))
                    .closeTime(LocalTime.of(18, 0))
                    .closed(false)
                    .build();
            when(vendorHoursRepository.findByVendorProfileIdAndWeekday(eq(10L), eq(DayOfWeek.THURSDAY)))
                    .thenReturn(Optional.of(hours));

            // Tomorrow (Thursday): 08:00 IST = 02:30 UTC (before 09:00 open)
            assertThatThrownBy(() -> service.validateSlot(vendor, "2026-10-22T02:30:00Z"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                    .hasMessage("Slot start time is before vendor opening time");
        }

        @Test
        @DisplayName("Closing time boundary: slot ending exactly at closing is valid, slot ending after closing is rejected")
        void enforcesClosingTimeBoundary() {
            VendorHours hours = VendorHours.builder()
                    .vendorProfile(vendor)
                    .weekday(DayOfWeek.WEDNESDAY)
                    .openTime(LocalTime.of(9, 0))
                    .closeTime(LocalTime.of(18, 0))
                    .closed(false)
                    .build();
            when(vendorHoursRepository.findByVendorProfileIdAndWeekday(eq(10L), eq(DayOfWeek.WEDNESDAY)))
                    .thenReturn(Optional.of(hours));

            // 17:00 IST to 18:00 IST (11:30 UTC): ends exactly at 18:00 closeTime -> valid
            SlotValidationService.ValidatedSlot validated = service.validateSlot(vendor, "2026-10-21T11:30:00Z");
            assertThat(validated.slotStartUtc()).isEqualTo(LocalDateTime.of(2026, 10, 21, 11, 30, 0));
            assertThat(validated.slotEndUtc()).isEqualTo(LocalDateTime.of(2026, 10, 21, 12, 30, 0));

            // 18:00 IST to 19:00 IST (12:30 UTC): ends after closing time -> rejected
            assertThatThrownBy(() -> service.validateSlot(vendor, "2026-10-21T12:30:00Z"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                    .hasMessage("Slot end time exceeds vendor closing time");
        }

        @Test
        @DisplayName("Rejects off-grid slot timestamp")
        void rejectsOffGridSlot() {
            VendorHours hours = VendorHours.builder()
                    .vendorProfile(vendor)
                    .weekday(DayOfWeek.WEDNESDAY)
                    .openTime(LocalTime.of(9, 0))
                    .closeTime(LocalTime.of(18, 0))
                    .closed(false)
                    .build();
            when(vendorHoursRepository.findByVendorProfileIdAndWeekday(eq(10L), eq(DayOfWeek.WEDNESDAY)))
                    .thenReturn(Optional.of(hours));

            // 60-minute duration with 09:00 open: grid slots are 09:00, 10:00, 11:00, 12:00, ...
            // 12:15 IST (06:45 UTC): 195 minutes since open, 195 % 60 != 0 -> off-grid
            assertThatThrownBy(() -> service.validateSlot(vendor, "2026-10-21T06:45:00Z"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                    .hasMessage("Slot is not aligned to vendor duration grid");
        }
    }
}
