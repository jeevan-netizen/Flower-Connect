package com.flowerconnect.b2b.service;

import com.flowerconnect.b2b.domain.B2BOrder;
import com.flowerconnect.b2b.domain.B2BOrder.PaymentStatus;
import com.flowerconnect.b2b.domain.B2BOrder.Status;
import com.flowerconnect.b2b.domain.B2BOrderItem;
import com.flowerconnect.b2b.dto.B2BOrderActionRequest;
import com.flowerconnect.b2b.dto.B2BOrderItemRequest;
import com.flowerconnect.b2b.dto.B2BOrderItemResponse;
import com.flowerconnect.b2b.dto.B2BOrderPageResponse;
import com.flowerconnect.b2b.dto.B2BOrderResponse;
import com.flowerconnect.b2b.dto.PlaceB2BOrderRequest;
import com.flowerconnect.b2b.dto.SellerSummary;
import com.flowerconnect.b2b.dto.B2BProductSummary;
import com.flowerconnect.b2b.repository.B2BOrderItemRepository;
import com.flowerconnect.b2b.repository.B2BOrderRepository;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.inventory.domain.StockDelta;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.inventory.service.InventoryService;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * B2B order service (Stage 1 of the B2B module).
 *
 * <p>Owns the B2B order state machine and every validation rule:
 * <ul>
 *   <li>Only {@code APPROVED} vendors may place or act on B2B orders.</li>
 *   <li>A vendor cannot order from itself.</li>
 *   <li>Every ordered product must belong to the selected seller, be
 *       {@code ACTIVE}, and have sufficient available stock.</li>
 *   <li>Quantities must be positive.</li>
 *   <li>Prices and totals are computed server-side; product name, unit
 *       and unit price are snapshots at placement time.</li>
 *   <li>Stock is deducted through {@link InventoryService} when the seller
 *       accepts, and restored when an accepted order is cancelled. The
 *       buyer's inventory is never increased.</li>
 * </ul>
 *
 * <p>Payment stays {@code UNPAID} in Stage 1. The settlement ledger is
 * deliberately not integrated here; e-bills and settlement are Stage 2.
 *
 * <p>All timestamps come from the injected {@link Clock}; {@code
 * Instant.now()} is never called directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class B2BOrderService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final B2BOrderRepository orderRepository;
    private final B2BOrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final Clock clock;

    // ------------------------------------------------------------------
    // Seller search
    // ------------------------------------------------------------------

    /**
     * Searches other approved vendors, optionally filtered by a query
     * matched against the business name. The calling vendor is excluded.
     */
    @Transactional(readOnly = true)
    public List<SellerSummary> searchSellers(String vendorEmail, String query) {
        VendorProfile caller = requireApprovedVendor(vendorEmail);
        List<VendorProfile> sellers = vendorProfileRepository.findAllByStatus(
                VendorProfile.Status.APPROVED);
        return sellers.stream()
                .filter(s -> !s.getId().equals(caller.getId()))
                .filter(s -> matchesQuery(s, query))
                .map(this::toSellerSummary)
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // Seller's orderable products
    // ------------------------------------------------------------------

    /**
     * Lists a seller's orderable products: {@code ACTIVE} products with
     * catalog price and currently available stock.
     */
    @Transactional(readOnly = true)
    public List<B2BProductSummary> listSellerProducts(String vendorEmail, Long sellerId) {
        requireApprovedVendor(vendorEmail); // caller must be approved
        VendorProfile seller = requireVendor(sellerId);
        if (seller.getStatus() != VendorProfile.Status.APPROVED) {
            throw BusinessException.forbidden("Seller is not approved");
        }

        List<Product> products = productRepository.findByVendorIdAndStatus(
                sellerId, ProductStatus.ACTIVE,
                org.springframework.data.domain.PageRequest.of(
                        0, Integer.MAX_VALUE,
                        org.springframework.data.domain.Sort.by("name").ascending()))
                .getContent();

        return products.stream()
                .map(p -> toProductSummary(p, sellerId))
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // Place order
    // ------------------------------------------------------------------

    /**
     * Places a new B2B order. Validates that the buyer is not the seller,
     * every product belongs to the seller and is active/orderable, and
     * quantities are positive with sufficient available stock. Prices and
     * totals are computed server-side and snapshotted.
     */
    @Transactional
    public B2BOrderResponse placeOrder(String buyerEmail, PlaceB2BOrderRequest request) {
        VendorProfile buyer = requireApprovedVendor(buyerEmail);
        VendorProfile seller = requireVendor(request.getSellerId());

        if (!seller.getStatus().equals(VendorProfile.Status.APPROVED)) {
            throw BusinessException.forbidden("Seller is not approved");
        }
        if (buyer.getId().equals(seller.getId())) {
            throw BusinessException.badRequest("A vendor cannot place an order with itself");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw BusinessException.badRequest("At least one item is required");
        }

        for (B2BOrderItemRequest item : request.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw BusinessException.badRequest("Quantity must be greater than zero");
            }
        }

        List<StockDelta> deltas = request.getItems().stream()
                .map(item -> {
                    Product product = productRepository.findById(item.getProductId())
                            .orElseThrow(() -> BusinessException.notFound(
                                    "Product not found: " + item.getProductId()));
                    if (!product.getVendor().getId().equals(seller.getId())) {
                        throw BusinessException.forbidden(
                                "Product " + item.getProductId() + " does not belong to seller " +
                                        seller.getId());
                    }
                    if (product.getStatus() != ProductStatus.ACTIVE) {
                        throw BusinessException.badRequest(
                                "Product " + product.getName() + " is not active");
                    }
                    Integer available = inventoryRepository.findByProductId(product.getId())
                            .map(inv -> inv.getAvailable())
                            .orElse(0);
                    if (available < item.getQuantity()) {
                        throw BusinessException.insufficientStock(
                                "Insufficient available stock for product '" + product.getName() +
                                "': " + available + " available, " + item.getQuantity() + " requested");
                    }
                    return new StockDelta(product.getId(), item.getQuantity());
                })
                .collect(Collectors.toList());

        String orderNo = generateOrderNo();
        B2BOrder order = B2BOrder.builder()
                .orderNo(orderNo)
                .buyer(buyer)
                .seller(seller)
                .status(Status.PLACED)
                .paymentStatus(PaymentStatus.UNPAID)
                .total(BigDecimal.ZERO)
                .orderNote(request.getOrderNote())
                .placedAt(LocalDateTime.now(clock))
                .build();

        BigDecimal grandTotal = BigDecimal.ZERO;
        for (int i = 0; i < request.getItems().size(); i++) {
            B2BOrderItemRequest itemRequest = request.getItems().get(i);
            StockDelta delta = deltas.get(i);
            Product product = productRepository.findById(delta.productId()).orElseThrow();

            BigDecimal unitPrice = product.getBasePrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(delta.quantity()));
            grandTotal = grandTotal.add(lineTotal);

            B2BOrderItem item = B2BOrderItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .unit(determineUnit(product))
                    .quantity(delta.quantity())
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .build();
            order.addItem(item);
        }

        order.setTotal(grandTotal);
        orderRepository.saveAndFlush(order);

        log.info("Placed B2B order {}: buyer={} seller={} total={}",
                orderNo, buyer.getId(), seller.getId(), grandTotal);
        return toResponse(order);
    }

    // ------------------------------------------------------------------
    // List orders (buyer or seller view)
    // ------------------------------------------------------------------

    /**
     * Lists the calling vendor's orders, either as buyer or as seller,
     * with an optional status filter. Newest first.
     */
    @Transactional(readOnly = true)
    public B2BOrderPageResponse listOrders(String vendorEmail, String role,
                                           Status status, int page, int size) {
        VendorProfile vendor = requireApprovedVendor(vendorEmail);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        List<B2BOrder> all;
        if ("SELLER".equalsIgnoreCase(role)) {
            all = (status != null)
                    ? orderRepository.findBySellerIdAndStatus(vendor.getId(), status)
                    : orderRepository.findBySellerId(vendor.getId());
        } else {
            // Default to buyer view for any non-SELLER role.
            all = (status != null)
                    ? orderRepository.findByBuyerIdAndStatus(vendor.getId(), status)
                    : orderRepository.findByBuyerId(vendor.getId());
        }

        long total = all.size();
        List<B2BOrder> content = all.stream()
                .sorted((a, b) -> {
                    int c = b.getPlacedAt().compareTo(a.getPlacedAt());
                    if (c != 0) return c;
                    return Long.compare(b.getId(), a.getId());
                })
                .skip((long) safePage * safeSize)
                .limit(safeSize)
                .collect(Collectors.toList());

        return B2BOrderPageResponse.builder()
                .content(content.stream().map(this::toResponse).collect(Collectors.toList()))
                .page(safePage)
                .size(safeSize)
                .totalElements(total)
                .totalPages((int) Math.ceil((double) total / safeSize))
                .first(safePage == 0)
                .last((safePage + 1) * safeSize >= total)
                .empty(content.isEmpty())
                .build();
    }

    // ------------------------------------------------------------------
    // Get order detail
    // ------------------------------------------------------------------

    /**
     * Returns a single B2B order. Only the buyer or the seller may read it.
     */
    @Transactional(readOnly = true)
    public B2BOrderResponse getOrder(String vendorEmail, Long orderId) {
        VendorProfile vendor = requireApprovedVendor(vendorEmail);
        B2BOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> BusinessException.notFound("B2B order not found"));
        if (!order.getBuyer().getId().equals(vendor.getId())
                && !order.getSeller().getId().equals(vendor.getId())) {
            throw BusinessException.forbidden("You are not a participant in this order");
        }
        return toResponse(order);
    }

    // ------------------------------------------------------------------
    // State transitions
    // ------------------------------------------------------------------

    /**
     * Seller accepts a PLACED order. Deducts stock through
     * {@link InventoryService} in the same transaction. If stock is
     * insufficient, the order is left unchanged.
     */
    @Transactional
    public B2BOrderResponse accept(String sellerEmail, Long orderId) {
        VendorProfile seller = requireApprovedVendor(sellerEmail);
        B2BOrder order = requireOrder(orderId);
        if (!order.getSeller().getId().equals(seller.getId())) {
            throw BusinessException.forbidden("Only the seller can accept this order");
        }
        if (order.getStatus() != Status.PLACED) {
            throw BusinessException.conflict(
                    "Order " + orderId + " is " + order.getStatus() + ", not PLACED; cannot accept");
        }

        List<StockDelta> deltas = order.getItems().stream()
                .map(item -> new StockDelta(item.getProduct().getId(), item.getQuantity()))
                .collect(Collectors.toList());
        inventoryService.deductForB2BAccept(deltas,
                userRepository.findByEmail(sellerEmail).orElseThrow(),
                "B2B order accepted - stock deducted");

        order.setStatus(Status.ACCEPTED);
        order.setUpdatedAt(LocalDateTime.now(clock));
        orderRepository.saveAndFlush(order);

        log.info("Seller {} accepted B2B order {}", seller.getId(), order.getOrderNo());
        return toResponse(order);
    }

    /**
     * Seller rejects a PLACED order.
     */
    @Transactional
    public B2BOrderResponse reject(String sellerEmail, Long orderId, String reason) {
        VendorProfile seller = requireApprovedVendor(sellerEmail);
        B2BOrder order = requireOrder(orderId);
        if (!order.getSeller().getId().equals(seller.getId())) {
            throw BusinessException.forbidden("Only the seller can reject this order");
        }
        if (order.getStatus() != Status.PLACED) {
            throw BusinessException.conflict(
                    "Order " + orderId + " is " + order.getStatus() + ", not PLACED; cannot reject");
        }

        order.setStatus(Status.REJECTED);
        order.setOrderNote(appendNote(order.getOrderNote(), reason));
        order.setUpdatedAt(LocalDateTime.now(clock));
        orderRepository.saveAndFlush(order);

        log.info("Seller {} rejected B2B order {}", seller.getId(), order.getOrderNo());
        return toResponse(order);
    }

    /**
     * Cancels an order. Buyer may cancel a PLACED order; either party may
     * cancel an ACCEPTED order, in which case the seller's deducted stock
     * is restored. No cancellation after SHIPPED.
     */
    @Transactional
    public B2BOrderResponse cancel(String actorEmail, Long orderId, String reason) {
        VendorProfile actor = requireApprovedVendor(actorEmail);
        B2BOrder order = requireOrder(orderId);

        boolean isBuyer = order.getBuyer().getId().equals(actor.getId());
        boolean isSeller = order.getSeller().getId().equals(actor.getId());
        if (!isBuyer && !isSeller) {
            throw BusinessException.forbidden("Only the buyer or seller can cancel this order");
        }

        Status current = order.getStatus();
        if (current == Status.PLACED) {
            if (!isBuyer) {
                throw BusinessException.forbidden("Only the buyer can cancel a PLACED order");
            }
            order.setStatus(Status.CANCELLED);
        } else if (current == Status.ACCEPTED) {
            // Restore the seller's deducted stock.
            List<StockDelta> deltas = order.getItems().stream()
                    .map(item -> new StockDelta(item.getProduct().getId(), item.getQuantity()))
                    .collect(Collectors.toList());
            inventoryService.restockCancelledOrder(deltas,
                    userRepository.findByEmail(actorEmail).orElseThrow(),
                    "B2B order cancelled - stock restored");
            order.setStatus(Status.CANCELLED);
        } else {
            throw BusinessException.conflict(
                    "Order " + orderId + " is " + current + "; cannot cancel after acceptance");
        }
        order.setOrderNote(appendNote(order.getOrderNote(), reason));
        order.setUpdatedAt(LocalDateTime.now(clock));
        orderRepository.saveAndFlush(order);

        log.info("{} cancelled B2B order {}", actor.getId(), order.getOrderNo());
        return toResponse(order);
    }

    /**
     * Seller ships an ACCEPTED order. Requires a shipment reference.
     */
    @Transactional
    public B2BOrderResponse ship(String sellerEmail, Long orderId, B2BOrderActionRequest request) {
        VendorProfile seller = requireApprovedVendor(sellerEmail);
        B2BOrder order = requireOrder(orderId);
        if (!order.getSeller().getId().equals(seller.getId())) {
            throw BusinessException.forbidden("Only the seller can ship this order");
        }
        if (order.getStatus() != Status.ACCEPTED) {
            throw BusinessException.conflict(
                    "Order " + orderId + " is " + order.getStatus() + ", not ACCEPTED; cannot ship");
        }
        if (request.getShipmentReference() == null || request.getShipmentReference().isBlank()) {
            throw BusinessException.badRequest("Shipment reference is required");
        }

        order.setStatus(Status.SHIPPED);
        order.setShipmentReference(request.getShipmentReference());
        order.setShippedAt(LocalDateTime.now(clock));
        order.setUpdatedAt(LocalDateTime.now(clock));
        orderRepository.saveAndFlush(order);

        log.info("Seller {} shipped B2B order {}", seller.getId(), order.getOrderNo());
        return toResponse(order);
    }

    /**
     * Seller delivers a SHIPPED order.
     */
    @Transactional
    public B2BOrderResponse deliver(String sellerEmail, Long orderId) {
        VendorProfile seller = requireApprovedVendor(sellerEmail);
        B2BOrder order = requireOrder(orderId);
        if (!order.getSeller().getId().equals(seller.getId())) {
            throw BusinessException.forbidden("Only the seller can deliver this order");
        }
        if (order.getStatus() != Status.SHIPPED) {
            throw BusinessException.conflict(
                    "Order " + orderId + " is " + order.getStatus() + ", not SHIPPED; cannot deliver");
        }

        order.setStatus(Status.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now(clock));
        order.setUpdatedAt(LocalDateTime.now(clock));
        orderRepository.saveAndFlush(order);

        log.info("Seller {} delivered B2B order {}", seller.getId(), order.getOrderNo());
        return toResponse(order);
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    private VendorProfile requireApprovedVendor(String email) {
        VendorProfile vendor = vendorProfileRepository.findByUserEmail(email)
                .orElseThrow(() -> BusinessException.notFound("Vendor profile not found"));
        if (vendor.getStatus() != VendorProfile.Status.APPROVED) {
            throw BusinessException.forbidden("Vendor profile is not APPROVED");
        }
        return vendor;
    }

    private VendorProfile requireVendor(Long vendorId) {
        return vendorProfileRepository.findById(vendorId)
                .orElseThrow(() -> BusinessException.notFound("Vendor not found"));
    }

    private B2BOrder requireOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> BusinessException.notFound("B2B order not found"));
    }

    // ------------------------------------------------------------------
    // Mappers
    // ------------------------------------------------------------------

    private B2BOrderResponse toResponse(B2BOrder order) {
        List<B2BOrderItemResponse> items = order.getItems().stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());
        return B2BOrderResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .buyerId(order.getBuyer().getId())
                .buyerName(order.getBuyer().getBusinessName())
                .sellerId(order.getSeller().getId())
                .sellerName(order.getSeller().getBusinessName())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .total(order.getTotal())
                .orderNote(order.getOrderNote())
                .shipmentReference(order.getShipmentReference())
                .shippedAt(order.getShippedAt())
                .deliveredAt(order.getDeliveredAt())
                .billNumber(order.getBillNumber())
                .placedAt(order.getPlacedAt())
                .updatedAt(order.getUpdatedAt())
                .items(items)
                .build();
    }

    private B2BOrderItemResponse toItemResponse(B2BOrderItem item) {
        return B2BOrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProductName())
                .unit(item.getUnit())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .lineTotal(item.getLineTotal())
                .build();
    }

    private SellerSummary toSellerSummary(VendorProfile seller) {
        return SellerSummary.builder()
                .vendorId(seller.getId())
                .businessName(seller.getBusinessName())
                .addressLine1(seller.getAddressLine1())
                .city(seller.getServiceLocation() != null
                        ? seller.getServiceLocation().getCity() : null)
                .area(seller.getServiceLocation() != null
                        ? seller.getServiceLocation().getArea() : null)
                .pincode(seller.getServiceLocation() != null
                        ? seller.getServiceLocation().getPincode() : null)
                .deliveryRadiusKm(seller.getDeliveryRadiusKm())
                .build();
    }

    private B2BProductSummary toProductSummary(Product product, Long sellerId) {
        Integer available = inventoryRepository.findByProductId(product.getId())
                .map(inv -> inv.getAvailable())
                .orElse(0);
        return B2BProductSummary.builder()
                .productId(product.getId())
                .productName(product.getName())
                .unitPrice(product.getBasePrice())
                .unit(determineUnit(product))
                .availableStock(available)
                .build();
    }

    private String determineUnit(Product product) {
        // No unit field on Product in Stage 1; default to "kg" for florist flowers.
        return "kg";
    }

    private String appendNote(String existing, String addition) {
        if (addition == null || addition.isBlank()) {
            return existing;
        }
        return (existing == null || existing.isBlank())
                ? addition.trim()
                : existing + " | " + addition.trim();
    }

    private String generateOrderNo() {
        String stamp = String.valueOf(LocalDateTime.now(clock).toEpochSecond(java.time.ZoneOffset.UTC));
        return "B2B-" + stamp + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private boolean matchesQuery(VendorProfile seller, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String q = query.toLowerCase(java.util.Locale.ROOT);
        return seller.getBusinessName().toLowerCase(java.util.Locale.ROOT).contains(q);
    }
}
