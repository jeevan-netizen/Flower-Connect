package com.flowerconnect.repository;

import com.flowerconnect.domain.ServiceLocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceLocationRepository extends JpaRepository<ServiceLocation, Long> {

    @Query("SELECT sl FROM ServiceLocation sl WHERE sl.city = :city")
    List<ServiceLocation> findByCity(String city);

    @Query("SELECT sl FROM ServiceLocation sl WHERE sl.pincode = :pincode")
    Optional<ServiceLocation> findByPincode(String pincode);

    @Query("SELECT sl FROM ServiceLocation sl WHERE sl.area = :area")
    List<ServiceLocation> findByArea(String area);

    @Query("SELECT sl FROM ServiceLocation sl WHERE sl.city = :city AND sl.area = :area")
    Optional<ServiceLocation> findByCityAndArea(String city, String area);

    @Query("SELECT DISTINCT sl.city FROM ServiceLocation sl")
    List<String> findDistinctCities();

    @Query("SELECT sl FROM ServiceLocation sl WHERE sl.city = :city AND sl.area = :area")
    Optional<ServiceLocation> findByCityAndAreaIgnoreCase(String city, String area);

    Page<ServiceLocation> findAll(Pageable pageable);

    @Query("SELECT sl FROM ServiceLocation sl WHERE (:pincode IS NULL OR sl.pincode = :pincode) AND (:area IS NULL OR sl.area = :area) ORDER BY sl.city, sl.area")
    Page<ServiceLocation> searchByPincodeAndArea(String pincode, String area, Pageable pageable);
}