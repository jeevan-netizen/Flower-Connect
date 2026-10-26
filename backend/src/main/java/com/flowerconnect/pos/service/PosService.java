package com.flowerconnect.pos.service;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.domain.StockDelta;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.inventory.service.InventoryService;
import com.flowerconnect.pos.config.PosProperties;
import com.flowerconnect.pos.domain.PosItem;
import com.flowerconnect.pos.domain.PosMoney;
import com.flowerconnect.pos.domain.PosTransaction;
import com.flowerconnect.pos.dto.PosSaleRequest;
import com.flowerconnect.pos.dto.PosSaleRequest.PosSaleLineRequest;
import com.flowerconnect.pos.dto.PosSaleResponse;
import com.flowerconnect.pos.dto.PosSummaryResponse;
import com.flowerconnect.pos.repository.PosItemRepository;
import com.flowerconnect.pos.repository.PosTransactionRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * POS walk-in sale service (plan Phase 11, tasks 11.2 and 11.3).
 *
 * <p>A POS sale is commission-free and outside the marketplace ledger: it
 * creates no order, order-item, payment, commission or settlement row. Its
 * items carry server-side name/price snapshots taken at sale time.
 *
 * <p>Every stock change goes through {@link InventoryService}, which takes
 * the pessimistic write lock on inventory rows in ascending product-id order
 * (D-24). A sale may consume only available stock
 * ({@code quantity - reservedQuantity}); reserved units for pending orders
 * are never touched.
 *
 * <p>Idempotency is keyed by the required {@code Idempotency-Key} header. A
 * completed sale with the same key and body is replayed without a second
 * sale; a same-key/different-body request is rejected with 409; concurrent
 * identical requests produce exactly one sale.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PosService {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final String REFERENCE_TYPE = "pos-transaction";

    private final PosTransactionRepository posTransactionRepository;
    private final PosItemRepository posItemRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final PosProperties posProperties;
    private final Clock clock;

    // ------------------------------------------------------------------
    // Quick sale
    // ------------------------------------------------------------------

    /**
     * Records a POS walk-in sale in one transaction.
     *
     * @param vendorEmail    the calling vendor's email (from the JWT subject)
     * @param idempotencyKey the required {@code Idempotency-Key} header value
     * @param request        the sale request (product IDs and quantities only)
     * @return the receipt response
     */
    @Transactional
    public PosSaleResponse recordSale(String vendorEmail, String idempotencyKey,
                                      PosSaleRequest request) {
        validateIdempotencyKey(idempotencyKey);
        validateRequest(request);

        VendorProfile vendor = requireVendor(vendorEmail);

        String fingerprint = computeFingerprint(request);

        // Idempotency check: existing record for this vendor + key.
        Optional<PosTransaction> existing =
                posTransactionRepository.findByVendorIdAndIdempotencyKey(vendor.getId(), idempotencyKey);

        if (existing.isPresent()) {
            PosTransaction record = existing.get();
            if (record.getCompletedAt() != null) {
                // Completed: replay only if the body matches.
                if (MessageDigest.isEqual(
                        record.getRequestFingerprint().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        fingerprint.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                    return buildResponse(record, vendor);
                }
                throw BusinessException.conflict(
                        "A sale with this idempotency key was already recorded with a different payload. " +
                                "Use a new idempotency key for a different sale.");
            }
            // In progress: a concurrent sale with the same key is mid-flight.
            throw BusinessException.conflict(
                    "A sale with this idempotency key is already in progress. Please wait and retry.");
        }

        // Validate products: existence, ownership, active status.
        List<PosSaleLineRequest> lines = mergeDuplicateItems(request.getItems());
        List<Product> products = validateProductsExistence(vendor, lines);

        // Check expiry and inventory existence on the unlocked read first (sellability gate).
        List<Inventory> inventories = validateInventories(products, lines);

        // Insert a pending idempotency record so concurrent identical requests
        // serialize on the unique (vendor_id, idempotency_key) constraint.
        PosTransaction pending = PosTransaction.builder()
                .vendor(vendor)
                .idempotencyKey(idempotencyKey)
                .requestFingerprint(fingerprint)
                .total(BigDecimal.ZERO)
                .paymentMethod(request.getPaymentMethod().name())
                .build();
        try {
            pending = posTransactionRepository.saveAndFlush(pending);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw BusinessException.conflict(
                    "A sale with this idempotency key is already in progress. Please wait and retry.");
        }

        // Lock inventory rows in ascending product id, check availability, deduct.
        List<StockDelta> deltas = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            deltas.add(new StockDelta(products.get(i).getId(), lines.get(i).getQuantity()));
        }
        User actor = requireActor(vendorEmail);
        inventoryService.deductPosStock(deltas, pending.getId(), REFERENCE_TYPE, actor);

        // Build line snapshots from server-side product data.
        BigDecimal grandTotal = BigDecimal.ZERO;
        List<PosItem> posItems = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Product product = products.get(i);
            PosSaleLineRequest line = lines.get(i);
            BigDecimal unitPrice = product.getBasePrice();
            BigDecimal lineTotal = PosMoney.lineTotal(unitPrice, line.getQuantity());
            grandTotal = grandTotal.add(lineTotal);

            PosItem item = PosItem.builder()
                    .transaction(pending)
                    .productId(product.getId())
                    .productNameSnapshot(product.getName())
                    .unitPriceSnapshot(unitPrice)
                    .quantity(line.getQuantity())
                    .lineTotal(lineTotal)
                    .build();
            posItems.add(item);
        }
        grandTotal = PosMoney.transactionTotal(
                posItems.stream().map(PosItem::getLineTotal).collect(Collectors.toList()));

        pending.setTotal(grandTotal);
        posTransactionRepository.saveAndFlush(pending);
        posItemRepository.saveAllAndFlush(posItems);
        pending.setCompletedAt(LocalDateTime.now(clock));
        posTransactionRepository.saveAndFlush(pending);

        log.info("Vendor {} recorded POS sale {}: {} line(s), total {}, method {}",
                vendorEmail, pending.getId(), lines.size(), grandTotal, request.getPaymentMethod());

        return buildResponse(pending, vendor);
    }

    /**
     * Retrieves a past POS sale receipt. Foreign sales are 403, missing sales 404.
     */
    @Transactional(readOnly = true)
    public PosSaleResponse getSale(String vendorEmail, Long transactionId) {
        VendorProfile vendor = requireVendor(vendorEmail);
        PosTransaction sale = posTransactionRepository.findById(transactionId)
                .orElseThrow(() -> BusinessException.notFound("POS sale not found"));
        if (!sale.getVendor().getId().equals(vendor.getId())) {
            throw BusinessException.forbidden("POS sale belongs to another vendor");
        }
        return buildResponse(sale, vendor);
    }

    // ------------------------------------------------------------------
    // Summary
    // ------------------------------------------------------------------

    /**
     * Today's POS summary for the calling vendor. "Today" is the calendar day
     * in the vendor's service-location timezone; timestamps are stored in UTC
     * and day boundaries are converted at query time using the injected Clock.
     */
    @Transactional(readOnly = true)
    public PosSummaryResponse getSummary(String vendorEmail, LocalDate date) {
        VendorProfile vendor = requireVendor(vendorEmail);
        ZoneId zoneId = ZoneId.of(vendor.getServiceLocation().getTimezone());

        ZonedDateTime startLocal = date.atStartOfDay(zoneId);
        ZonedDateTime endLocal = startLocal.plusDays(1);
        LocalDateTime from = startLocal.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime to = endLocal.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDateTime();

        List<PosTransaction> sales =
                posTransactionRepository.findByVendorIdAndCreatedAtBetween(
                        vendor.getId(), from, to,
                        org.springframework.data.domain.Pageable.unpaged()).getContent();

        int transactionCount = sales.size();
        BigDecimal totalRevenue = sales.stream()
                .map(PosTransaction::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, PosSummaryResponse.PosPaymentMethodBreakdown> breakdownMap = new LinkedHashMap<>();
        for (PosTransaction sale : sales) {
            String method = sale.getPaymentMethod();
            PosSummaryResponse.PosPaymentMethodBreakdown entry = breakdownMap.computeIfAbsent(method,
                    m -> PosSummaryResponse.PosPaymentMethodBreakdown.builder()
                            .paymentMethod(m)
                            .transactionCount(0)
                            .revenue(BigDecimal.ZERO).build());
            entry.setTransactionCount(entry.getTransactionCount() + 1);
            entry.setRevenue(entry.getRevenue().add(sale.getTotal()));
        }

        return PosSummaryResponse.builder()
                .date(date)
                .timezone(zoneId.getId())
                .transactionCount(transactionCount)
                .totalRevenue(PosMoney.round(totalRevenue))
                .breakdownByPaymentMethod(new ArrayList<>(breakdownMap.values()))
                .build();
    }

    // ------------------------------------------------------------------
    // Validation helpers
    // ------------------------------------------------------------------

    public void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw BusinessException.badRequest("Idempotency-Key header is required");
        }
        if (!UUID_PATTERN.matcher(idempotencyKey.trim()).matches()) {
            throw BusinessException.badRequest("Idempotency-Key must be a canonical UUID");
        }
    }

    public void validateRequest(PosSaleRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw BusinessException.badRequest("Items are required");
        }
        if (request.getPaymentMethod() == null) {
            throw BusinessException.badRequest("Payment method is required");
        }
        int maxLines = posProperties.getMaxLinesPerSale();
        if (request.getItems().size() > maxLines) {
            throw BusinessException.badRequest(
                    "A POS sale may contain at most " + maxLines + " line(s)");
        }
        int maxQty = posProperties.getMaxQuantityPerLine();
        for (PosSaleLineRequest line : request.getItems()) {
            if (line.getQuantity() == null || line.getQuantity() <= 0) {
                throw BusinessException.badRequest("Quantity must be greater than zero");
            }
            if (line.getQuantity() > maxQty) {
                throw BusinessException.badRequest(
                        "Quantity per line must not exceed " + maxQty);
            }
        }
    }

    public List<PosSaleLineRequest> mergeDuplicateItems(List<PosSaleLineRequest> items) {
        Map<Long, PosSaleLineRequest> merged = new LinkedHashMap<>();
        for (PosSaleLineRequest item : items) {
            Long productId = item.getProductId();
            if (merged.containsKey(productId)) {
                PosSaleLineRequest existing = merged.get(productId);
                existing.setQuantity(existing.getQuantity() + item.getQuantity());
            } else {
                merged.put(productId, item);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private List<Product> validateProductsExistence(VendorProfile vendor, List<PosSaleLineRequest> lines) {
        List<Product> products = new ArrayList<>();
        for (PosSaleLineRequest line : lines) {
            Product product = productRepository.findById(line.getProductId())
                    .orElseThrow(() -> BusinessException.notFound("Product not found: " + line.getProductId()));
            if (!product.getVendor().getId().equals(vendor.getId())) {
                throw BusinessException.forbidden("Product does not belong to this vendor: " + line.getProductId());
            }
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw BusinessException.conflict("Product is not active: " + product.getName());
            }
            products.add(product);
        }
        return products;
    }

    private List<Inventory> validateInventories(List<Product> products, List<PosSaleLineRequest> lines) {
        LocalDate today = LocalDate.now(clock);
        List<Inventory> inventories = new ArrayList<>();
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            Inventory inventory = inventoryRepository.findByProductId(product.getId())
                    .orElseThrow(() -> BusinessException.notFound("Inventory not found for product: " + product.getId()));
            if (inventory.getExpiryDate() != null && inventory.getExpiryDate().isBefore(today)) {
                throw BusinessException.conflict("Product is expired: " + product.getName());
            }
            inventories.add(inventory);
        }
        return inventories;
    }

    private PosSaleResponse buildResponse(PosTransaction sale, VendorProfile vendor) {
        List<PosItem> items = posItemRepository.findByTransactionIdOrderByIdAsc(sale.getId());
        List<PosSaleResponse.PosSaleLineResponse> lines = items.stream()
                .map(item -> PosSaleResponse.PosSaleLineResponse.builder()
                        .productId(item.getProductId())
                        .productNameSnapshot(item.getProductNameSnapshot())
                        .unitPriceSnapshot(item.getUnitPriceSnapshot())
                        .quantity(item.getQuantity())
                        .lineTotal(item.getLineTotal())
                        .build())
                .toList();
        return PosSaleResponse.builder()
                .transactionId(sale.getId())
                .vendorName(vendor.getBusinessName())
                .createdAt(sale.getCreatedAt())
                .paymentMethod(sale.getPaymentMethod())
                .lines(lines)
                .total(sale.getTotal())
                .build();
    }

    private VendorProfile requireVendor(String vendorEmail) {
        return vendorProfileRepository.findByUserEmail(vendorEmail)
                .orElseThrow(() -> BusinessException.notFound("Vendor profile not found"));
    }

    private User requireActor(String vendorEmail) {
        return userRepository.findByEmail(vendorEmail)
                .orElseThrow(() -> BusinessException.notFound("User not found"));
    }

    public String computeFingerprint(PosSaleRequest request) {
        try {
            StringBuilder sb = new StringBuilder();
            List<PosSaleLineRequest> sorted = request.getItems().stream()
                    .sorted(java.util.Comparator.comparing(PosSaleLineRequest::getProductId))
                    .toList();
            for (PosSaleLineRequest item : sorted) {
                sb.append(item.getProductId()).append(":").append(item.getQuantity()).append(";");
            }
            sb.append("|paymentMethod:").append(request.getPaymentMethod().name());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
