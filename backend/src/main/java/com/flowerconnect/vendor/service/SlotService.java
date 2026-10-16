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
        
        // Use today if date is null
        if (date == null) {
            date = LocalDate.now(zoneId);
        }
        
        // Check if date is in the past
        LocalDate today = LocalDate.now(zoneId);
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
        
        // Get current time in vendor's timezone
        Instant instant = clock.instant();
        ZonedDateTime nowInTz = instant.atZone(zoneId);
        
        // Determine effective open window: [max(now_in_tz, open_time) + prepTime, close_time]
        LocalTime openTime = vendorHours.getOpenTime();
        LocalTime closeTime = vendorHours.getCloseTime();
        int prepTimeMinutes = vendor.getPrepTimeMinutes();
        int slotDurationMinutes = vendor.getSlotDurationMinutes();
        
        // Convert to minutes since midnight for easier calculations
        long openTimeMinutes = openTime.toSecondOfDay() / 60;
        long closeTimeMinutes = closeTime.toSecondOfDay() / 60;
        long nowTimeMinutes = nowInTz.toLocalTime().toSecondOfDay() / 60;
        
        boolean isOvernight = closeTimeMinutes < openTimeMinutes;
        
        // For overnight hours, adjust close time to be on the next day
        long adjustedCloseTimeMinutes = isOvernight ? closeTimeMinutes + 24 * 60 : closeTimeMinutes;
        
        // Calculate window start: max(now_in_tz, open_time) + prepTime
        long baseTimeMinutes = Math.max(nowTimeMinutes, openTimeMinutes);
        long windowStartTimeMinutes = baseTimeMinutes + prepTimeMinutes;
        
        // Check if window start is after close time (adjusted for overnight)
        if (windowStartTimeMinutes >= adjustedCloseTimeMinutes) {
            // No slots today, try next day
            return getAvailableSlots(vendorId, addressId, date.plusDays(1), email);
        }
        
        // Generate candidate slots
        List<SlotResponse> slots = new ArrayList<>();
        long slotStartTimeMinutes = windowStartTimeMinutes;
        
        while (true) {
            long slotEndTimeMinutes = slotStartTimeMinutes + slotDurationMinutes;
            
            // Check if slot would exceed close time
            if (slotEndTimeMinutes > adjustedCloseTimeMinutes) {
                break; // Slot would exceed close time
            }
            
            // Convert back to LocalTime for slot start and end
            LocalTime slotStartTime = LocalTime.ofSecondOfDay(slotStartTimeMinutes * 60);
            LocalTime slotEndTime = LocalTime.ofSecondOfDay(slotEndTimeMinutes * 60);
            
            // Determine the actual date for this slot (important for overnight hours)
            LocalTime slotStartLocalTime = slotStartTime;
            LocalDate slotStartDate = date;
            if (isOvernight && slotStartTimeMinutes >= 24 * 60) {
                // This slot start time is actually on the next day
                slotStartLocalTime = slotStartTime.minusHours(24);
                slotStartDate = date.plusDays(1);
            }
            
            LocalTime slotEndLocalTime = slotEndTime;
            LocalDate slotEndDate = date;
            if (isOvernight && slotEndTimeMinutes >= 24 * 60) {
                // This slot end time is actually on the next day
                slotEndLocalTime = slotEndTime.minusHours(24);
                slotEndDate = date.plusDays(1);
            }
            
            // Create slot start and end times as LocalDateTime in vendor timezone
            LocalDateTime slotStartLdt = LocalDateTime.of(slotStartDate, slotStartLocalTime);
            LocalDateTime slotEndLdt = LocalDateTime.of(slotEndDate, slotEndLocalTime);
            
            // Convert to UTC for storage/comparison with orders
            ZonedDateTime slotStartZdt = slotStartLdt.atZone(zoneId);
            ZonedDateTime slotEndZdt = slotEndLdt.atZone(zoneId);
            LocalDateTime slotStartUtc = slotStartZdt.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            LocalDateTime slotEndUtc = slotEndZdt.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            
            // Check if slot is beyond horizon (from now in vendor timezone)
            ZonedDateTime horizonLimit = nowInTz.plusDays(maxBookingHorizonDays);
            if (slotStartZdt.isAfter(horizonLimit)) {
                break; // Slot is beyond booking horizon
            }
            
            // Apply lead-time floor: exclude slots starting within minLeadTimeMinutes of "now"
            ZonedDateTime leadTimeLimit = nowInTz.plusMinutes(minLeadTimeMinutes);
            boolean withinLeadTime = slotStartZdt.isBefore(leadTimeLimit);
            
            // Check capacity: count existing orders for this vendor+slot
            // Only PLACED and CONFIRMED orders count as booked per design
            long bookedCount = orderRepository.countBookedOrdersForSlot(
                    vendorId, slotStartUtc);
            
            boolean available = bookedCount < vendor.getMaxOrdersPerSlot() && !withinLeadTime;
            
            // Create slot response
            SlotResponse slot = SlotResponse.builder()
                    .slotStart(slotStartUtc)
                    .slotEnd(slotEndUtc)
                    .weekday(dayOfWeek)
                    .available(available)
                    .build();
            
            slots.add(slot);
            
            // Move to next slot
            slotStartTimeMinutes += slotDurationMinutes;
        }
        
        return slots;
    }
}