package com.flowerconnect.geo.mapper;

import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.geo.dto.ServiceLocationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
    public interface ServiceLocationMapper {

        ServiceLocationMapper INSTANCE = Mappers.getMapper(ServiceLocationMapper.class);

        default ServiceLocationResponse toHierarchicalResponse(List<ServiceLocation> locations) {
            if (locations.isEmpty()) {
                return null;
            }
            String city = locations.get(0).getCity();
            Map<String, List<ServiceLocation>> byArea = locations.stream()
                    .collect(Collectors.groupingBy(ServiceLocation::getArea));

            List<ServiceLocationResponse.AreaResponse> areas = byArea.entrySet().stream()
                    .map(entry -> {
                        ServiceLocation first = entry.getValue().get(0);
                        return ServiceLocationResponse.AreaResponse.builder()
                                .area(entry.getKey())
                                .pincode(first.getPincode())
                                .latitude(first.getLatitude())
                                .longitude(first.getLongitude())
                                .build();
                    })
                    .toList();

            return ServiceLocationResponse.builder()
                    .city(city)
                    .areas(areas)
                    .build();
        }

        default List<ServiceLocationResponse> toFlatResponse(List<ServiceLocation> locations) {
            if (locations.isEmpty()) {
                return List.of();
            }
            Map<String, List<ServiceLocation>> byCity = locations.stream()
                    .collect(Collectors.groupingBy(ServiceLocation::getCity));

            return byCity.entrySet().stream()
                    .map(cityEntry -> {
                        Map<String, List<ServiceLocation>> byArea = cityEntry.getValue().stream()
                                .collect(Collectors.groupingBy(ServiceLocation::getArea));

                        List<ServiceLocationResponse.AreaResponse> areas = byArea.entrySet().stream()
                                .map(areaEntry -> {
                                    ServiceLocation first = areaEntry.getValue().get(0);
                                    return ServiceLocationResponse.AreaResponse.builder()
                                            .area(areaEntry.getKey())
                                            .pincode(first.getPincode())
                                            .latitude(first.getLatitude())
                                            .longitude(first.getLongitude())
                                            .build();
                                })
                                .toList();

                        return ServiceLocationResponse.builder()
                                .city(cityEntry.getKey())
                                .areas(areas)
                                .build();
                    })
                    .toList();
        }
    }