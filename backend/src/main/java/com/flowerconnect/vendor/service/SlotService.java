package com.flowerconnect.vendor.service;

import com.flowerconnect.customer.service.AddressService;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorHours;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorHoursRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.vendor.dto.SlotResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlotService {

    private final VendorProfileRepository vendorProfileRepository;
    private final ServiceLocationRepository serviceLocationRepository;
    private final VendorHoursRepository vendorHoursRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final AddressService addressService;
    private final Clock clock;

    @Value("${app.checkout.min-lead-time-minutes}")
    private int minLeadTimeMinutes;

    @Value("${app.checkout.max-booking-horizon-days}")
    private int maxBookingHorizonDays;

    /**
     * Returns available slot windows for a vendor on a given date at a given address.
     * 
     * @param vendorId The ID of the vendor
     * @param addressId The ID of the customer's address
     * @param date The date for which to check slots (defaults to today if null)
     * @param email The email of the authenticated customer (from JWT)
     * @return List of available slot responses
     */
    @Transactional(readOnly = true)
    public List<SlotResponse> getAvailableSlots(Long vendorId, Long addressId, LocalDate date, String email) {
        // Validate vendor exists and is approved
        VendorProfile vendor = vendorProfileRepository.findById(vendorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Vendor not found"));
        
        if (!vendor.getStatus().equals(VendorProfile.Status.APPROVED)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Vendor is not approved");
        }
        
        if (!vendor.isAcceptingOrders()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Vendor is not accepting orders");
        }
        
        // Validate address exists and belongs to the customer
        User user = userRepository.findByEmailWithRole(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
        
        // This will throw BusinessException.notFound if address doesn't exist or doesn't belong to user
        addressService.getById(email, addressId);
        
        // Get vendor's service location for timezone
        ServiceLocation serviceLocation = vendor.getServiceLocation();
        if (serviceLocation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Vendor service location not found");
        }
        
        // Validate timezone
        String timezoneId = serviceLocation.getTimezone();
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezoneId);
        } catch (DateTimeException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid timezone configuration for vendor");
        }
        
        // Get current time in vendor's timezone
        Instant instant = clock.instant();
        ZonedDateTime nowInTz = instant.atZone(zoneId);
        LocalDate today = nowInTz.toLocalDate();

        // Use today if date is null
        if (date == null) {
            date = today;
        }

        // Check if date is in the past
        if (date.isBefore(today)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Date cannot be in the past");
        }

        // Check if date is beyond the booking horizon
        if (date.isAfter(today.plusDays(maxBookingHorizonDays))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Date is beyond the booking horizon");
        }

        // Get vendor hours for the day of week
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        Optional<VendorHours> optionalVendorHours = vendorHoursRepository.findByVendorProfileIdAndWeekday(vendorId, dayOfWeek);

        // If vendor is closed on this day, return empty list
        if (optionalVendorHours.isEmpty() || optionalVendorHours.get().isClosed()) {
            return new ArrayList<>();
        }
        VendorHours vendorHours = optionalVendorHours.get();

        LocalTime openTime = vendorHours.getOpenTime();
        LocalTime closeTime = vendorHours.getCloseTime();
        if (openTime == null || closeTime == null) {
            return new ArrayList<>();
        }

        int slotDurationMinutes = vendor.getSlotDurationMinutes();
        if (slotDurationMinutes <= 0) {
            return new ArrayList<>();
        }

        // If today and operating hours have already passed, return empty list (no cross-day recursion)
        if (date.isEqual(today) && !nowInTz.toLocalTime().isBefore(closeTime)) {
            return new ArrayList<>();
        }

        // Generate candidate slots on the operating grid starting strictly at openTime
        List<SlotResponse> slots = new ArrayList<>();
        LocalTime candidateStart = openTime;
        ZonedDateTime leadTimeLimit = nowInTz.plusMinutes(minLeadTimeMinutes);

        while (candidateStart.plusMinutes(slotDurationMinutes).compareTo(closeTime) <= 0) {
            LocalTime candidateEnd = candidateStart.plusMinutes(slotDurationMinutes);

            LocalDateTime slotStartLdt = LocalDateTime.of(date, candidateStart);
            LocalDateTime slotEndLdt = LocalDateTime.of(date, candidateEnd);

            ZonedDateTime slotStartZdt = slotStartLdt.atZone(zoneId);
            ZonedDateTime slotEndZdt = slotEndLdt.atZone(zoneId);

            LocalDateTime slotStartUtc = slotStartZdt.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            LocalDateTime slotEndUtc = slotEndZdt.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

            boolean withinLeadTime = slotStartZdt.isBefore(leadTimeLimit);
            long bookedCount = orderRepository.countBookedOrdersForSlot(vendorId, slotStartUtc);
            boolean available = bookedCount < vendor.getMaxOrdersPerSlot() && !withinLeadTime;

            slots.add(SlotResponse.builder()
                    .slotStart(slotStartUtc)
                    .slotEnd(slotEndUtc)
                    .weekday(dayOfWeek)
                    .available(available)
                    .build());

            candidateStart = candidateStart.plusMinutes(slotDurationMinutes);
        }

        return slots;
    }
}