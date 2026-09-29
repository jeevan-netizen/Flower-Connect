package com.flowerconnect.repository;

import com.flowerconnect.domain.VendorHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

@Repository
public interface VendorHoursRepository extends JpaRepository<VendorHours, Long> {

    List<VendorHours> findByVendorProfileIdOrderByWeekdayAsc(Long vendorProfileId);

    Optional<VendorHours> findByVendorProfileIdAndWeekday(Long vendorProfileId, DayOfWeek weekday);
}
