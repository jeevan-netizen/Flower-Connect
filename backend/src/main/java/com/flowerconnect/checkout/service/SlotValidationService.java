
package com.flowerconnect.checkout.service;

import com.flowerconnect.domain.VendorHours;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.VendorHoursRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

/**
 * Shared service for delivery slot timestamp parsing and structural validation.
 *
 * <p>Enforces strict ISO-8601 UTC parsing and validates delivery-slot constraints
 * (lead time, booking horizon, vendor operating day, opening/closing boundaries,
 * and grid alignment) for both quote generation and checkout execution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SlotValidationService {

    private final Clock clock;
    private final VendorHoursRepository vendorHoursRepository;

    @Value("${app.checkout.min-lead-time-minutes:120}")
    private int minLeadTimeMinutes;

    @Value("${app.checkout.max-booking-horizon-days:7}")
    private int maxBookingHorizonDays;

    /**
     * Strict ISO-8601 grammar:
     * Accepts:
     * - Canonical UTC local: uuuu-MM-dd'T'HH:mm or uuuu-MM-dd'T'HH:mm:ss
     * - Explicit Zulu: same followed by 'Z'
     * - Explicit offset: same followed by '±HH:mm'
     * Rejects:
     * - date-only, fractional seconds, malformed offsets, trailing chars
     */
    private static final Pattern STRICT_ISO_PATTERN = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?(Z|[+-]\\d{2}:\\d{2})?$"
    );

    private static final DateTimeFormatter STRICT_LOCAL_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd'T'HH:mm")
            .optionalStart()
            .appendPattern(":ss")
            .optionalEnd()
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);

    private static final DateTimeFormatter STRICT_OFFSET_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd'T'HH:mm")
            .optionalStart()
            .appendPattern(":ss")
            .optionalEnd()
            .appendOffset("+HH:MM", "Z")
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);

    public record ValidatedSlot(
            LocalDateTime slotStartUtc,
            LocalDateTime slotEndUtc,
            ZonedDateTime slotInVendorTz
    ) {}

    /**
     * Parses the incoming timestamp strictly and normalizes it to UTC {@link LocalDateTime}.
     *
     * @param slotStartStr the raw timestamp string
     * @return the normalized UTC LocalDateTime
     * @throws BusinessException (HTTP 400 VALIDATION_FAILED) if the format is invalid
     */
    public LocalDateTime parseAndNormalize(String slotStartStr) {
        if (slotStartStr == null || slotStartStr.isBlank()) {
            throw BusinessException.badRequest("Invalid slotStart format (expected ISO-8601)");
        }

        if (!STRICT_ISO_PATTERN.matcher(slotStartStr).matches()) {
            throw BusinessException.badRequest("Invalid slotStart format (expected ISO-8601)");
        }

        try {
            if (slotStartStr.endsWith("Z") || (slotStartStr.contains("+") || (slotStartStr.lastIndexOf('-') > 10))) {
                OffsetDateTime odt = OffsetDateTime.parse(slotStartStr, STRICT_OFFSET_FORMATTER);
                return odt.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
            } else {
                return LocalDateTime.parse(slotStartStr, STRICT_LOCAL_FORMATTER);
            }
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest("Invalid slotStart format (expected ISO-8601)");
        }
    }

    /**
     * Validates slot structure, timezone conversion, past/lead-time, horizon, operating hours,
     * and grid alignment for a vendor.
     *
     * @param vendor the vendor profile
     * @param slotStartStr the raw timestamp string
     * @return the validated slot container with UTC start/end and vendor-zoned timestamp
     * @throws BusinessException if any validation rule is violated
     */
    public ValidatedSlot validateSlot(VendorProfile vendor, String slotStartStr) {
        LocalDateTime slotStartUtc = parseAndNormalize(slotStartStr);

        Integer slotDuration = vendor.getSlotDurationMinutes();
        if (slotDuration == null || slotDuration <= 0) {
            throw BusinessException.badRequest("Vendor slot duration configuration is invalid");
        }

        if (vendor.getServiceLocation() == null || vendor.getServiceLocation().getTimezone() == null) {
            throw BusinessException.badRequest("Vendor service location or timezone unconfigured");
        }

        ZoneId vendorZoneId;
        try {
            vendorZoneId = ZoneId.of(vendor.getServiceLocation().getTimezone());
        } catch (Exception e) {
            throw BusinessException.badRequest("Vendor service location or timezone unconfigured");
        }

        ZonedDateTime nowInVendorTz = clock.instant().atZone(vendorZoneId);
        ZonedDateTime slotInVendorTz = slotStartUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(vendorZoneId);

        // 1. Past check: strictly before now
        if (slotInVendorTz.isBefore(nowInVendorTz)) {
            throw BusinessException.badRequest("Selected slot is in the past");
        }

        // 2. Minimum lead time check (inclusive at boundary)
        ZonedDateTime leadTimeThreshold = nowInVendorTz.plusMinutes(minLeadTimeMinutes);
        if (slotInVendorTz.isBefore(leadTimeThreshold)) {
            throw BusinessException.badRequest("Selected slot violates minimum lead time");
        }

        // 3. Maximum booking horizon check (inclusive calendar day)
        LocalDate todayInVendorTz = nowInVendorTz.toLocalDate();
        LocalDate maxHorizonDate = todayInVendorTz.plusDays(maxBookingHorizonDays);
        LocalDate slotDate = slotInVendorTz.toLocalDate();
        if (slotDate.isAfter(maxHorizonDate)) {
            throw BusinessException.badRequest("Date is beyond the booking horizon");
        }

        // 4. Operating hours check
        VendorHours hours = vendorHoursRepository
                .findByVendorProfileIdAndWeekday(vendor.getId(), slotDate.getDayOfWeek())
                .orElse(null);

        if (hours == null || hours.isClosed()) {
            throw BusinessException.badRequest("Vendor is closed on " + slotDate.getDayOfWeek());
        }

        if (hours.getOpenTime() == null || hours.getCloseTime() == null) {
            throw BusinessException.badRequest("Vendor operating hours not configured");
        }

        LocalTime slotLocalStart = slotInVendorTz.toLocalTime();
        if (slotLocalStart.isBefore(hours.getOpenTime())) {
            throw BusinessException.badRequest("Slot start time is before vendor opening time");
        }

        LocalTime slotLocalEnd = slotLocalStart.plusMinutes(slotDuration);
        if (slotLocalEnd.isAfter(hours.getCloseTime())) {
            throw BusinessException.badRequest("Slot end time exceeds vendor closing time");
        }

        // 5. Operating grid alignment check
        long minutesSinceOpen = Duration.between(hours.getOpenTime(), slotLocalStart).toMinutes();
        if (minutesSinceOpen % slotDuration != 0) {
            throw BusinessException.badRequest("Slot is not aligned to vendor duration grid");
        }

        LocalDateTime slotEndUtc = slotStartUtc.plusMinutes(slotDuration);
        return new ValidatedSlot(slotStartUtc, slotEndUtc, slotInVendorTz);
    }
}
