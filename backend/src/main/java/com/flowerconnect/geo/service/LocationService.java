package com.flowerconnect.geo.service;

import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.geo.dto.ServiceLocationResponse;
import com.flowerconnect.geo.mapper.ServiceLocationMapper;
import com.flowerconnect.repository.ServiceLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ServiceLocationRepository repository;
    private final ServiceLocationMapper mapper;

    public List<ServiceLocationResponse> findAllHierarchical() {
        List<ServiceLocation> allLocations = repository.findAll(Sort.by("city", "area"));
        ServiceLocationResponse response = mapper.toHierarchicalResponse(allLocations);
        return response != null ? List.of(response) : List.of();
    }

    public PageResponse<ServiceLocationResponse> search(String pincode, String area, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("city", "area"));
        Page<ServiceLocation> result = repository.searchByPincodeAndArea(pincode, area, pageable);

        List<ServiceLocationResponse> content = mapper.toFlatResponse(result.getContent());

        return PageResponse.<ServiceLocationResponse>builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .empty(result.isEmpty())
                .build();
    }

    public Optional<ServiceLocation> findByPincode(String pincode) {
        return repository.findByPincode(pincode);
    }

    public List<ServiceLocation> findByArea(String area) {
        return repository.findByArea(area);
    }

    public List<ServiceLocation> findByCity(String city) {
        return repository.findByCity(city);
    }

    public Optional<ServiceLocation> findByCityAndArea(String city, String area) {
        return repository.findByCityAndArea(city, area);
    }

    public List<String> findDistinctCities() {
        return repository.findDistinctCities();
    }
}