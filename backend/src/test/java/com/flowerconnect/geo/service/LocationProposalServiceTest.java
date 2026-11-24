package com.flowerconnect.geo.service;

import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.geo.domain.ServiceLocationProposal;
import com.flowerconnect.geo.dto.ApproveServiceLocationProposalRequest;
import com.flowerconnect.geo.dto.ServiceLocationProposalResponse;
import com.flowerconnect.geo.mapper.ServiceLocationProposalMapper;
import com.flowerconnect.geo.repository.ServiceLocationProposalRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link LocationProposalService}: proposal submission is
 * idempotent, approval creates a real service location, and rejection records
 * a reason. Neither test hits the database.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocationProposalServiceTest {

    @Mock
    private ServiceLocationProposalRepository proposalRepository;

    @Mock
    private ServiceLocationRepository serviceLocationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LocationProposalService service;

    private static final User PROPOSER = User.builder().id(1L).email("florist@example.com").build();
    private static final User REVIEWER = User.builder().id(9L).email("admin@example.com").build();

    @Test
    void submittingANewProposalRecordsItAsPending() {
        when(proposalRepository.findByCityAndAreaAndStatus("Bengaluru", "Indiranagar",
                ServiceLocationProposal.Status.PENDING)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(PROPOSER));
        when(proposalRepository.save(any())).thenAnswer(invocation -> {
            ServiceLocationProposal p = invocation.getArgument(0);
            p.setId(7L);
            return p;
        });

        ServiceLocationProposal proposal = service.submitProposal(
                "Bengaluru", "Indiranagar", "560038", "12 MG Road", null, 1L, "florist@example.com");

        assertThat(proposal.getStatus()).isEqualTo(ServiceLocationProposal.Status.PENDING);
        assertThat(proposal.getCity()).isEqualTo("Bengaluru");
        assertThat(proposal.getArea()).isEqualTo("Indiranagar");
        assertThat(proposal.getPincode()).isEqualTo("560038");
        assertThat(proposal.getAddressLine1()).isEqualTo("12 MG Road");
        assertThat(proposal.getProposedByUser()).isSameAs(PROPOSER);
        assertThat(proposal.getProposedByEmail()).isEqualTo("florist@example.com");
    }

    @Test
    void resubmittingTheSamePendingAreaReturnsTheExistingProposal() {
        ServiceLocationProposal existing = ServiceLocationProposal.builder()
                .id(3L).city("Bengaluru").area("Indiranagar").pincode("560038")
                .addressLine1("12 MG Road").status(ServiceLocationProposal.Status.PENDING)
                .proposedByUser(PROPOSER).proposedByEmail("florist@example.com").build();
        when(proposalRepository.findByCityAndAreaAndStatus("Bengaluru", "Indiranagar",
                ServiceLocationProposal.Status.PENDING)).thenReturn(Optional.of(existing));

        ServiceLocationProposal proposal = service.submitProposal(
                "Bengaluru", "Indiranagar", "560038", "12 MG Road", null, 1L, "florist@example.com");

        assertThat(proposal).isSameAs(existing);
        verify(proposalRepository, never()).save(any());
    }

    @Test
    void approvalCreatesARealServiceLocationAndFlipsTheProposal() {
        ServiceLocationProposal proposal = ServiceLocationProposal.builder()
                .id(5L).city("Bengaluru").area("Jayanagar").pincode("560041")
                .addressLine1("4th T Block").status(ServiceLocationProposal.Status.PENDING)
                .proposedByUser(PROPOSER).build();
        when(proposalRepository.findById(5L)).thenReturn(Optional.of(proposal));
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(REVIEWER));
        when(proposalRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApproveServiceLocationProposalRequest request = new ApproveServiceLocationProposalRequest();
        request.setLatitude(new BigDecimal("12.9232"));
        request.setLongitude(new BigDecimal("77.5836"));
        request.setTimezone("Asia/Kolkata");

        com.flowerconnect.geo.dto.ServiceLocationProposalResponse response =
                service.approveProposal(5L, 9L, request);

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(proposal.getStatus()).isEqualTo(ServiceLocationProposal.Status.APPROVED);
        assertThat(proposal.getReviewedByUser()).isSameAs(REVIEWER);
        assertThat(proposal.getReviewedAt()).isNotNull();

        ArgumentCaptor<ServiceLocation> captor = ArgumentCaptor.forClass(ServiceLocation.class);
        verify(serviceLocationRepository).saveAndFlush(captor.capture());
        ServiceLocation created = captor.getValue();
        assertThat(created.getCity()).isEqualTo("Bengaluru");
        assertThat(created.getArea()).isEqualTo("Jayanagar");
        assertThat(created.getPincode()).isEqualTo("560041");
        assertThat(created.getLatitude()).isEqualByComparingTo(new BigDecimal("12.9232"));
        assertThat(created.getLongitude()).isEqualByComparingTo(new BigDecimal("77.5836"));
        assertThat(created.getTimezone()).isEqualTo("Asia/Kolkata");
    }

    @Test
    void rejectingANonPendingProposalIsRefused() {
        ServiceLocationProposal proposal = ServiceLocationProposal.builder()
                .id(5L).status(ServiceLocationProposal.Status.APPROVED).build();
        when(proposalRepository.findById(5L)).thenReturn(Optional.of(proposal));

        assertThatThrownBy(
                () -> service.rejectProposal(5L, 9L, "duplicate area"))
                .isInstanceOf(com.flowerconnect.exception.BusinessException.class);

        verify(proposalRepository, never()).save(any());
    }

    @Test
    void rejectionRecordsTheReason() {
        ServiceLocationProposal proposal = ServiceLocationProposal.builder()
                .id(5L).city("Bengaluru").area("Indiranagar")
                .status(ServiceLocationProposal.Status.PENDING).proposedByUser(PROPOSER).build();
        when(proposalRepository.findById(5L)).thenReturn(Optional.of(proposal));
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(REVIEWER));

        service.rejectProposal(5L, 9L, "already covered by Indiranagar");

        assertThat(proposal.getStatus()).isEqualTo(ServiceLocationProposal.Status.REJECTED);
        assertThat(proposal.getRejectionReason()).isEqualTo("already covered by Indiranagar");
        assertThat(proposal.getReviewedByUser()).isSameAs(REVIEWER);
        assertThat(proposal.getReviewedAt()).isNotNull();
    }

    @Test
    void toResponseMapsIdAndStatus() {
        ServiceLocationProposal proposal = ServiceLocationProposal.builder()
                .id(42L).city("Bengaluru").area("Indiranagar")
                .status(ServiceLocationProposal.Status.PENDING).build();
        ServiceLocationProposalResponse response =
                ServiceLocationProposalMapper.INSTANCE.toResponse(proposal);
        assertThat(response.getId()).isEqualTo(42L);
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getCity()).isEqualTo("Bengaluru");
        assertThat(response.getArea()).isEqualTo("Indiranagar");
    }
}
