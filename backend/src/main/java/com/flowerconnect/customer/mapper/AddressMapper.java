package com.flowerconnect.customer.mapper;

import com.flowerconnect.customer.domain.Address;
import com.flowerconnect.customer.dto.AddressResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * Address entity-to-response mapping (plan task 4.1).
 *
 * <p>The entity's {@code defaultAddress} property is exposed under the
 * same name in the API vocabulary — an {@code isDefault} property name
 * would serialize as {@code default}, because Jackson strips the "is"
 * prefix from Lombok's generated boolean getter. The service location
 * is flattened to its id and the city/area/pincode the client renders.
 */
@Mapper(componentModel = "spring")
public interface AddressMapper {

    AddressMapper INSTANCE = Mappers.getMapper(AddressMapper.class);

    @Mapping(target = "serviceLocationId", source = "serviceLocation.id")
    @Mapping(target = "city", source = "serviceLocation.city")
    @Mapping(target = "area", source = "serviceLocation.area")
    @Mapping(target = "pincode", source = "serviceLocation.pincode")
    @Mapping(target = "defaultAddress", source = "defaultAddress")
    AddressResponse toResponse(Address address);

    List<AddressResponse> toResponse(List<Address> addresses);
}
