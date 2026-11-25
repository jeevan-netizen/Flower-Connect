package com.flowerconnect.farmer.service;

import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.farmer.domain.FarmerDeposit;
import com.flowerconnect.farmer.domain.FarmerVendorLink;
import com.flowerconnect.farmer.dto.FarmerDepositRequest;
import com.flowerconnect.farmer.dto.FarmerDepositResponse;
import com.flowerconnect.farmer.policy.WeightDeductionPolicy;
import com.flowerconnect.farmer.repository.FarmerDepositRepository;
import com.flowerconnect.farmer.repository.FarmerVendorLinkRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Records vendor-entered flower deposits on farmer/vendor links.
 *
 * <p>Ownership is derived from the authenticated florist's vendor profile: a
 * florist may only record deposits on links belonging to their own vendor, and
 * only while the link is {@code ACTIVE}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FarmerDepositService {

    private final FarmerDepositRepository depositRepository;
    private final FarmerVendorLinkRepository linkRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final Clock clock;

    /**
     * Records a deposit on the given link, owned by the given vendor.
     *
     * @param vendorId the authenticated florist's vendor id (ownership boundary)
     * @param linkId   the farmer/vendor link to deposit against
     * @param request  the deposit details (flower type, gross kg, price per kg,
     *                 optional deposited-at)
     * @return the persisted deposit response
     */
    @Transactional
    public FarmerDepositResponse recordDeposit(Long vendorId, Long linkId, FarmerDepositRequest request) {
        vendorProfileRepository.findById(vendorId)
                .orElseThrow(() -> BusinessException.notFound("Vendor not found"));

        FarmerVendorLink link = linkRepository.findByIdAndVendorId(linkId, vendorId)
                .orElseGet(() -> {
                    if (linkRepository.findById(linkId).isPresent()) {
                        throw BusinessException.forbidden("This link belongs to another vendor");
                    }
                    throw BusinessException.notFound("Link not found");
                });

        if (link.getStatus() != FarmerVendorLink.Status.ACTIVE) {
            throw BusinessException.conflict(
                    "Deposits require an active link (current status: " + link.getStatus() + ")");
        }

        BigDecimal grossKg = request.getGrossKg().setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal pricePerKg = request.getPricePerKg().setScale(2, BigDecimal.ROUND_HALF_UP);

        BigDecimal deductionKg = WeightDeductionPolicy.deductionKg(grossKg);
        BigDecimal netKg = grossKg.subtract(deductionKg).setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal amount = netKg.multiply(pricePerKg).setScale(2, BigDecimal.ROUND_HALF_UP);

        LocalDateTime depositedAt = request.getDepositedAt() != null
                ? request.getDepositedAt()
                : LocalDateTime.now(clock);

        FarmerDeposit deposit = FarmerDeposit.builder()
                .linkId(linkId)
                .flowerType(request.getFlowerType())
                .grossKg(grossKg)
                .deductionKg(deductionKg)
                .netKg(netKg)
                .pricePerKg(pricePerKg)
                .amount(amount)
                .depositedAt(depositedAt)
                .build();

        depositRepository.save(deposit);
        log.debug("Recorded deposit {} for link {}: gross {} kg, deduction {} kg, net {} kg, amount {}",
                deposit.getId(), linkId, grossKg, deductionKg, netKg, amount);

        return toResponse(deposit);
    }

    private FarmerDepositResponse toResponse(FarmerDeposit deposit) {
        return FarmerDepositResponse.builder()
                .id(deposit.getId())
                .linkId(deposit.getLinkId())
                .flowerType(deposit.getFlowerType())
                .grossKg(deposit.getGrossKg())
                .deductionKg(deposit.getDeductionKg())
                .netKg(deposit.getNetKg())
                .pricePerKg(deposit.getPricePerKg())
                .amount(deposit.getAmount())
                .depositedAt(deposit.getDepositedAt())
                .createdAt(deposit.getCreatedAt())
                .build();
    }
}
