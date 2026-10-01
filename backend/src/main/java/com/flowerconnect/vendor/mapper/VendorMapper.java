package com.flowerconnect.vendor.mapper;

import com.flowerconnect.domain.VendorHours;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.vendor.dto.VendorHoursResponse;
import com.flowerconnect.vendor.dto.VendorProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface VendorMapper {

    VendorMapper INSTANCE = Mappers.getMapper(VendorMapper.class);

    @Mapping(target = "weekday", source = "weekday")
    @Mapping(target = "openTime", source = "openTime")
    @Mapping(target = "closeTime", source = "closeTime")
    @Mapping(target = "closed", source = "closed")
    VendorHoursResponse toHoursResponse(VendorHours hours);

    List<VendorHoursResponse> toHoursResponses(List<VendorHours> hours);

    /**
     * Maps a profile together with its already-loaded opening hours. Hours are
     * passed in rather than derived so the caller controls the query (single
     * profile, or one batched query for a page of profiles).
     */
    @Mapping(target = "ownerEmail", source = "profile.user.email")
    @Mapping(target = "city", source = "profile.serviceLocation.city")
    @Mapping(target = "area", source = "profile.serviceLocation.area")
    @Mapping(target = "pincode", source = "profile.serviceLocation.pincode")
    @Mapping(target = "serviceLocationId", source = "profile.serviceLocation.id")
    @Mapping(target = "status", source = "profile.status")
    @Mapping(target = "hours", source = "hours")
    VendorProfileResponse toResponse(VendorProfile profile, List<VendorHours> hours);
}
