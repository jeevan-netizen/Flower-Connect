package com.flowerconnect.geo.mapper;

import com.flowerconnect.geo.domain.ServiceLocationProposal;
import com.flowerconnect.geo.dto.ServiceLocationProposalResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ServiceLocationProposalMapper {

    ServiceLocationProposalMapper INSTANCE = Mappers.getMapper(ServiceLocationProposalMapper.class);

    @Mapping(target = "status", source = "status")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "reviewedAt", source = "reviewedAt")
    ServiceLocationProposalResponse toResponse(ServiceLocationProposal proposal);
}
