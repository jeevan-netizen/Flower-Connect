package com.flowerconnect.farmer.service;

import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.farmer.domain.FarmerDeposit;
import com.flowerconnect.farmer.domain.FarmerPayment;
import com.flowerconnect.farmer.domain.FarmerVendorLink;
import com.flowerconnect.farmer.dto.FarmerCollectionRequest;
import com.flowerconnect.farmer.dto.FarmerCollectionResponse;
import com.flowerconnect.farmer.dto.PassbookEntryResponse;
import com.flowerconnect.farmer.dto.PassbookResponse;
import com.flowerconnect.farmer.repository.FarmerDepositRepository;
import com.flowerconnect.farmer.repository.FarmerPaymentRepository;
import com.flowerconnect.farmer.repository.FarmerVendorLinkRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Farmer collection requests and vendor decisions (Stage 3 of the farmer module).
 *
 * <p>Outstanding balance is always computed as {@code sum(deposits) - sum(CONFIRMED
 * collections)}; PENDING and REJECTED collections remain visible in the passbook
 * but do not reduce the balance. No balance column is stored.
 *
 * <p>Ownership is derived from the authenticated principal: a farmer may only act
 * on their own links and a florist may only act on links belonging to their own
 * vendor profile.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FarmerCollectionService {

    private final FarmerVendorLinkRepository linkRepository;
    private final FarmerPaymentRepository paymentRepository;
    private final FarmerDepositRepository depositRepository;
    private final UserRepository userRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final Clock clock;

    /**
     * Requests a collection on the farmer's own {@code ACTIVE} link. The request is
     * saved as {@code PENDING}; the balance does not change. At most one
     * {@code PENDING} collection may exist per link.
     */
    @Transactional
    public FarmerCollectionResponse requestCollection(Long farmerId, Long linkId, FarmerCollectionRequest request) {
        // Lock the link row so the status check and the one-PENDING rule are
        // evaluated under a consistent snapshot and concurrent requests serialise.
        FarmerVendorLink link = linkRepository.findByIdAndFarmerIdForUpdate(linkId, farmerId)
                .orElseGet(() -> {
                    if (linkRepository.findById(linkId).isPresent()) {
                        throw BusinessException.forbidden("This link belongs to another farmer");
                    }
                    throw BusinessException.notFound("Link not found");
                });

        if (link.getStatus() != FarmerVendorLink.Status.ACTIVE) {
            throw BusinessException.conflict(
                    "Collections require an active link (current status: " + link.getStatus() + ")");
        }

        if (paymentRepository.existsByLinkIdAndStatus(linkId, FarmerPayment.Status.PENDING)) {
            throw BusinessException.conflict("A collection request is already pending for this link");
        }

        BigDecimal amount = request.getAmount().setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal balance = outstandingBalance(linkId);
        if (amount.compareTo(balance) > 0) {
            throw BusinessException.conflict(
                    "Collection amount " + amount + " exceeds outstanding balance " + balance);
        }

        FarmerPayment payment = FarmerPayment.builder()
                .linkId(linkId)
                .amount(amount)
                .mode(FarmerPayment.Mode.valueOf(request.getMode().name()))
                .note(request.getNote())
                .status(FarmerPayment.Status.PENDING)
                .requestedAt(LocalDateTime.now(clock))
                .build();
        paymentRepository.save(payment);
        log.debug("Recorded collection request {} for link {}: amount {}", payment.getId(), linkId, amount);

        return toResponse(payment);
    }

    /**
     * Confirms a pending collection inside one transaction, holding a lock on the
     * link row. Recomputes the outstanding balance while holding the lock and
     * rejects (leaving the request PENDING) when the balance is too low.
     */
    @Transactional
    public FarmerCollectionResponse confirmCollection(Long vendorId, Long paymentId) {
        FarmerPayment paymentRef = paymentRepository.findById(paymentId)
                .orElseThrow(() -> BusinessException.notFound("Collection not found"));

        // Lock the link row to enforce vendor ownership.
        FarmerVendorLink link = linkRepository.findByIdAndVendorIdForUpdate(paymentRef.getLinkId(), vendorId)
                .orElseGet(() -> {
                    if (linkRepository.findById(paymentRef.getLinkId()).isPresent()) {
                        throw BusinessException.forbidden("This link belongs to another vendor");
                    }
                    throw BusinessException.notFound("Link not found");
                });

        // Lock the payment row and re-fetch so the status check is under the lock.
        FarmerPayment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> BusinessException.notFound("Collection not found"));

        if (payment.getStatus() != FarmerPayment.Status.PENDING) {
            throw BusinessException.conflict(
                    "Cannot confirm a collection in status " + payment.getStatus() + " (expected PENDING)");
        }

        BigDecimal balance = outstandingBalance(payment.getLinkId());
        BigDecimal amount = payment.getAmount().setScale(2, BigDecimal.ROUND_HALF_UP);
        if (amount.compareTo(balance) > 0) {
            throw BusinessException.conflict(
                    "Collection amount " + amount + " exceeds current outstanding balance " + balance);
        }

        payment.setStatus(FarmerPayment.Status.CONFIRMED);
        payment.setDecidedAt(LocalDateTime.now(clock));
        paymentRepository.save(payment);
        log.debug("Confirmed collection {} for link {}: amount {}", payment.getId(), link.getId(), amount);

        return toResponse(payment);
    }

    /**
     * Rejects a pending collection. Does not change the balance. A PENDING request
     * may still be rejected when its link is suspended.
     */
    @Transactional
    public FarmerCollectionResponse rejectCollection(Long vendorId, Long paymentId) {
        FarmerPayment paymentRef = paymentRepository.findById(paymentId)
                .orElseThrow(() -> BusinessException.notFound("Collection not found"));

        FarmerVendorLink link = linkRepository.findByIdAndVendorIdForUpdate(paymentRef.getLinkId(), vendorId)
                .orElseGet(() -> {
                    if (linkRepository.findById(paymentRef.getLinkId()).isPresent()) {
                        throw BusinessException.forbidden("This link belongs to another vendor");
                    }
                    throw BusinessException.notFound("Link not found");
                });

        FarmerPayment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> BusinessException.notFound("Collection not found"));

        if (payment.getStatus() != FarmerPayment.Status.PENDING) {
            throw BusinessException.conflict(
                    "Cannot reject a collection in status " + payment.getStatus() + " (expected PENDING)");
        }

        payment.setStatus(FarmerPayment.Status.REJECTED);
        payment.setDecidedAt(LocalDateTime.now(clock));
        paymentRepository.save(payment);
        log.debug("Rejected collection {} for link {}: amount {}", payment.getId(), link.getId(),
                payment.getAmount());

        return toResponse(payment);
    }

    /**
     * Returns the passbook for a link: link summary, outstanding balance, and a
     * combined deposit/collection entry list ordered by date then id, each with a
     * running balance. Accessible by the link's farmer or its vendor.
     */
    @Transactional(readOnly = true)
    public PassbookResponse getPassbook(Long linkId, Long actorId, String actorRole) {
        FarmerVendorLink link;
        if ("FARMER".equals(actorRole)) {
            link = linkRepository.findByIdAndFarmerId(linkId, actorId)
                    .orElseThrow(() -> BusinessException.notFound("Link not found"));
        } else if ("FLORIST".equals(actorRole)) {
            link = linkRepository.findByIdAndVendorId(linkId, actorId)
                    .orElseThrow(() -> BusinessException.notFound("Link not found"));
        } else {
            throw BusinessException.forbidden("Only a farmer or a vendor may view the passbook");
        }

        BigDecimal balance = outstandingBalance(linkId);

        List<FarmerDeposit> deposits = depositRepository.findByLinkId(linkId);
        List<FarmerPayment> collections = paymentRepository.findByLinkId(linkId);
        List<PassbookEntryResponse> entries = mergeEntries(deposits, collections);

        String farmerName = userRepository.findById(link.getFarmerId())
                .map(User::getFullName).orElse("unknown");
        String vendorName = vendorProfileRepository.findById(link.getVendorId())
                .map(VendorProfile::getBusinessName).orElse("unknown");

        return PassbookResponse.builder()
                .linkId(link.getId())
                .farmerId(link.getFarmerId())
                .farmerName(farmerName)
                .vendorId(link.getVendorId())
                .vendorName(vendorName)
                .linkStatus(link.getStatus().name())
                .outstandingBalance(balance)
                .entries(entries)
                .build();
    }

    /**
     * Returns the passbook for a link without the farmer/vendor ownership check,
     * so an administrator may view any link's ledger. The calculation
     * (outstanding balance, merged entries, running balance) is identical to
     * {@link #getPassbook}; only the access boundary differs.
     */
    @Transactional(readOnly = true)
    public PassbookResponse getPassbookForAdmin(Long linkId) {
        FarmerVendorLink link = linkRepository.findById(linkId)
                .orElseThrow(() -> BusinessException.notFound("Link not found"));

        BigDecimal balance = outstandingBalance(linkId);

        List<FarmerDeposit> deposits = depositRepository.findByLinkId(linkId);
        List<FarmerPayment> collections = paymentRepository.findByLinkId(linkId);
        List<PassbookEntryResponse> entries = mergeEntries(deposits, collections);

        String farmerName = userRepository.findById(link.getFarmerId())
                .map(User::getFullName).orElse("unknown");
        String vendorName = vendorProfileRepository.findById(link.getVendorId())
                .map(VendorProfile::getBusinessName).orElse("unknown");

        return PassbookResponse.builder()
                .linkId(link.getId())
                .farmerId(link.getFarmerId())
                .farmerName(farmerName)
                .vendorId(link.getVendorId())
                .vendorName(vendorName)
                .linkStatus(link.getStatus().name())
                .outstandingBalance(balance)
                .entries(entries)
                .build();
    }

    /**
     * Lists collections on links belonging to the given vendor, filtered by
     * status, for the vendor's collection queue.
     */
    @Transactional(readOnly = true)
    public List<FarmerCollectionResponse> listCollectionsByStatus(Long vendorId,
                                                                  FarmerPayment.Status status) {
        if (!vendorProfileRepository.existsById(vendorId)) {
            throw BusinessException.notFound("Vendor not found");
        }
        List<Long> linkIds = linkRepository.findByVendorId(vendorId).stream()
                .map(FarmerVendorLink::getId)
                .collect(Collectors.toList());
        if (linkIds.isEmpty()) {
            return List.of();
        }
        return paymentRepository.findByLinkIdInAndStatus(linkIds, status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /* ------------------------------------------------------------- private */

    private BigDecimal outstandingBalance(Long linkId) {
        BigDecimal deposits = depositRepository.sumAmountByLinkId(linkId);
        BigDecimal confirmedCollections = paymentRepository.sumAmountByLinkIdAndStatus(linkId,
                FarmerPayment.Status.CONFIRMED);
        return (deposits == null ? BigDecimal.ZERO : deposits)
                .subtract(confirmedCollections == null ? BigDecimal.ZERO : confirmedCollections);
    }

    private List<PassbookEntryResponse> mergeEntries(List<FarmerDeposit> deposits,
                                                     List<FarmerPayment> collections) {
        List<Entry> all = new ArrayList<>();
        for (FarmerDeposit d : deposits) {
            all.add(new Entry(d.getDepositedAt(), d.getId(), depositEntry(d)));
        }
        for (FarmerPayment p : collections) {
            all.add(new Entry(p.getRequestedAt(), p.getId(), collectionEntry(p)));
        }
        all.sort(Comparator.comparing((Entry e) -> e.date).thenComparing(e -> e.id));

        BigDecimal running = BigDecimal.ZERO;
        List<PassbookEntryResponse> result = new ArrayList<>();
        for (Entry e : all) {
            PassbookEntryResponse response = e.response;
            if (response.getType() == PassbookEntryResponse.Type.DEPOSIT) {
                running = running.add(response.getAmount());
            } else if (response.getStatus() == PassbookEntryResponse.Status.CONFIRMED) {
                running = running.subtract(response.getAmount());
            }
            // PENDING and REJECTED collections do not change the running balance.
            response.setRunningBalance(running);
            result.add(response);
        }
        return result;
    }

    private PassbookEntryResponse depositEntry(FarmerDeposit d) {
        return PassbookEntryResponse.builder()
                .id(d.getId())
                .type(PassbookEntryResponse.Type.DEPOSIT)
                .date(d.getDepositedAt())
                .amount(d.getAmount())
                .flowerType(d.getFlowerType())
                .grossKg(d.getGrossKg())
                .deductionKg(d.getDeductionKg())
                .netKg(d.getNetKg())
                .pricePerKg(d.getPricePerKg())
                .build();
    }

    private PassbookEntryResponse collectionEntry(FarmerPayment p) {
        return PassbookEntryResponse.builder()
                .id(p.getId())
                .type(PassbookEntryResponse.Type.COLLECTION)
                .date(p.getRequestedAt())
                .amount(p.getAmount())
                .mode(PassbookEntryResponse.Mode.valueOf(p.getMode().name()))
                .status(PassbookEntryResponse.Status.valueOf(p.getStatus().name()))
                .note(p.getNote())
                .build();
    }

    private FarmerCollectionResponse toResponse(FarmerPayment p) {
        return FarmerCollectionResponse.builder()
                .id(p.getId())
                .linkId(p.getLinkId())
                .amount(p.getAmount())
                .mode(FarmerCollectionResponse.Mode.valueOf(p.getMode().name()))
                .note(p.getNote())
                .status(FarmerCollectionResponse.Status.valueOf(p.getStatus().name()))
                .requestedAt(p.getRequestedAt())
                .decidedAt(p.getDecidedAt())
                .build();
    }

    /* Sort helper for merging deposits and collections by date then id. */
    private static class Entry {
        final LocalDateTime date;
        final Long id;
        final PassbookEntryResponse response;

        Entry(LocalDateTime date, Long id, PassbookEntryResponse response) {
            this.date = date;
            this.id = id;
            this.response = response;
        }
    }
}
