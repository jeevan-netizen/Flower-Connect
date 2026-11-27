package com.flowerconnect.geo.service;

import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.geo.domain.ServiceLocationProposal;
import com.flowerconnect.geo.dto.ApproveServiceLocationProposalRequest;
import com.flowerconnect.geo.dto.ServiceLocationProposalResponse;
import com.flowerconnect.geo.mapper.ServiceLocationProposalMapper;
import com.flowerconnect.geo.repository.ServiceLocationProposalRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service-area proposals submitted during vendor registration when the
 * vendor's shop area is not yet a seeded service location.
 *
 * A proposal is recorded in {@link ServiceLocationProposal.Status#PENDING} and
 * reviewed by an administrator. Approval creates the real
 * {@link ServiceLocation} row (with administrator-supplied coordinates and
 * timezone -- data the registration form cannot and must not invent), at which
 * point the area appears in the customer "Set your delivery location" picker.
 * Rejection records a reason.
 *
 * PENDING proposals are intentionally never exposed by
 * {@code GET /api/v1/locations}, so a proposed area is not publicly
 * discoverable or deliverable until approved.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationProposalService {

    private final ServiceLocationProposalRepository proposalRepository;
    private final ServiceLocationRepository serviceLocationRepository;
    private final UserRepository userRepository;

    /**
     * Submits a new service-area proposal, idempotently: a PENDING proposal for
     * the same (city, area) is returned rather than duplicated.
     */
    @Transactional
    public ServiceLocationProposal submitProposal(String city, String area, String pincode,
                                                  String addressLine1, String addressLine2,
                                                  Long proposedByUserId, String proposedByEmail) {
        String normalizedCity = city.trim();
        String normalizedArea = area.trim();

        ServiceLocationProposal existing = proposalRepository
                .findByCityAndAreaAndStatus(normalizedCity, normalizedArea,
                        ServiceLocationProposal.Status.PENDING)
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        User proposer = proposedByUserId != null
                ? userRepository.findById(proposedByUserId).orElse(null)
                : null;

        ServiceLocationProposal proposal = ServiceLocationProposal.builder()
                .city(normalizedCity)
                .area(normalizedArea)
                .pincode(pincode.trim())
                .addressLine1(addressLine1.trim())
                .addressLine2(addressLine2 == null ? null : addressLine2.trim())
                .status(ServiceLocationProposal.Status.PENDING)
                .proposedByUser(proposer)
                .proposedByEmail(proposedByEmail)
                .build();

        return proposalRepository.save(proposal);
    }

    @Transactional(readOnly = true)
    public List<ServiceLocationProposalResponse> listPending() {
        return proposalRepository.findAll().stream()
                .filter(p -> p.getStatus() == ServiceLocationProposal.Status.PENDING)
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .map(ServiceLocationProposalMapper.INSTANCE::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ServiceLocationProposalResponse findById(Long proposalId) {
        ServiceLocationProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> BusinessException.notFound("Service location proposal not found"));
        return ServiceLocationProposalMapper.INSTANCE.toResponse(proposal);
    }

    /**
     * Approves a proposal: flips it to APPROVED and creates the real
     * {@link ServiceLocation} row using the administrator-supplied coordinates
     * and timezone. The registration form must not supply or invent these.
     */
    @Transactional
    public ServiceLocationProposalResponse approveProposal(Long proposalId, Long reviewedByUserId,
                                                           ApproveServiceLocationProposalRequest request) {
        ServiceLocationProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> BusinessException.notFound("Service location proposal not found"));
        if (proposal.getStatus() != ServiceLocationProposal.Status.PENDING) {
            throw BusinessException.badRequest("Proposal is already " + proposal.getStatus());
        }

        User reviewer = reviewedByUserId != null
                ? userRepository.findById(reviewedByUserId).orElseThrow(
                        () -> new IllegalStateException("Reviewing administrator not found"))
                : null;

        ServiceLocation location = ServiceLocation.builder()
                .city(proposal.getCity())
                .area(proposal.getArea())
                .pincode(proposal.getPincode())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .timezone(request.getTimezone())
                .build();
        serviceLocationRepository.saveAndFlush(location);

        proposal.setStatus(ServiceLocationProposal.Status.APPROVED);
        proposal.setReviewedByUser(reviewer);
        proposal.setReviewedAt(java.time.LocalDateTime.now());
        proposalRepository.saveAndFlush(proposal);

        log.info("Approved service-location proposal {} as service location {}",
                proposal.getId(), location.getId());
        return ServiceLocationProposalMapper.INSTANCE.toResponse(proposal);
    }

    /**
     * Rejects a proposal with a required reason.
     */
    @Transactional
    public ServiceLocationProposalResponse rejectProposal(Long proposalId, Long reviewedByUserId,
                                                          String reason) {
        ServiceLocationProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> BusinessException.notFound("Service location proposal not found"));
        if (proposal.getStatus() != ServiceLocationProposal.Status.PENDING) {
            throw BusinessException.badRequest("Proposal is already " + proposal.getStatus());
        }

        User reviewer = reviewedByUserId != null
                ? userRepository.findById(reviewedByUserId).orElseThrow(
                        () -> new IllegalStateException("Reviewing administrator not found"))
                : null;

        proposal.setStatus(ServiceLocationProposal.Status.REJECTED);
        proposal.setRejectionReason(reason);
        proposal.setReviewedByUser(reviewer);
        proposal.setReviewedAt(java.time.LocalDateTime.now());
        proposalRepository.saveAndFlush(proposal);

        return ServiceLocationProposalMapper.INSTANCE.toResponse(proposal);
    }
}
