package com.flowerconnect.checkout.service;

import com.flowerconnect.cart.domain.Cart;
import com.flowerconnect.cart.domain.CartItem;
import com.flowerconnect.cart.repository.CartItemRepository;
import com.flowerconnect.cart.repository.CartRepository;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.checkout.domain.CheckoutIdempotency;
import com.flowerconnect.checkout.dto.CheckoutItemQuote;
import com.flowerconnect.checkout.dto.CheckoutQuoteRequest;
import com.flowerconnect.checkout.dto.CheckoutQuoteResponse;
import com.flowerconnect.checkout.dto.CheckoutRequest;
import com.flowerconnect.checkout.dto.CheckoutResponse;
import com.flowerconnect.checkout.dto.CheckoutShopQuote;
import com.flowerconnect.checkout.repository.CheckoutIdempotencyRepository;
import com.flowerconnect.config.AppProperties;
import com.flowerconnect.config.CheckoutProperties;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.domain.StockMovement;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.inventory.repository.StockMovementRepository;
import com.flowerconnect.customer.repository.AddressRepository;
import com.flowerconnect.notification.event.OrderPlaced;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.OrderItem;
import com.flowerconnect.order.domain.OrderStatusHistory;
import com.flowerconnect.order.repository.OrderItemRepository;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.repository.OrderStatusHistoryRepository;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.domain.PaymentIntent;
import com.flowerconnect.payment.domain.PaymentIntent.IntentStatus;
import com.flowerconnect.payment.repository.PaymentIntentRepository;
import com.flowerconnect.payment.repository.PaymentRepository;
import com.flowerconnect.payment.service.PaymentService;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Checkout quote service (plan task 5.3).
 *
 * <p>Generates a read-only quote for the customer's current cart against a
 * delivery address and per-vendor slot selection. The quote performs all
 * validations that checkout will later re-check under the inventory lock,
 * but makes no mutations: no stock reservation, no order creation, no cart
 * clearing.
 *
 * <p>Validations per shop (vendor):
 * <ol>
 *   <li>Vendor is {@code APPROVED} and {@code acceptingOrders = true} — 403/409.</li>
 *   <li>Address is within {@code delivery_radius_km} — 400 if not.</li>
 *   <li>Subtotal meets {@code min_order_amount} — 400 {@code MINIMUM_ORDER} if not.</li>
 *   <li>Selected slot is valid and has capacity — 409 {@code SLOT_FULL} if not.</li>
 *   <li>Per-item available stock ≥ cart quantity — 409 {@code INSUFFICIENT_STOCK} if not.</li>
 * </ol>
 *
 * <p>Price calculation (decision C-1): the {@code priceSnapshot} on each cart
 * item is authoritative for the order total. The quote re-reads the current
 * {@code products.base_price} and flags any drift via {@code priceChanged}
 * on the item and a {@code "price_changed_since_add_to_cart"} warning on the
 * shop. The customer must explicitly confirm a changed quote before checkout
 * can proceed.
 *
 * <p>All monetary arithmetic uses {@code BigDecimal} with {@code HALF_UP}
 * scale 2. Prices are tax-inclusive (D-3) — no tax line is added.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final CheckoutProperties checkoutProperties;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final CheckoutIdempotencyRepository idempotencyRepository;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final AppProperties appProperties;
    private final Clock clock;
    private final SlotValidationService slotValidationService;
    private final ApplicationEventPublisher eventPublisher;

    private static final BigDecimal EARTH_RADIUS_KM = new BigDecimal("6371");
    private static final int SCALE_2 = 2;
    private static final int SCALE_4 = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Generates a checkout quote for the authenticated customer's cart.
     *
     * @param customerEmail the caller's email (from JWT subject)
     * @param request       the quote request with addressId and per-vendor slot selection
     * @return the quote with per-shop breakdown, totals, warnings, and errors
     * @throws BusinessException if the cart is empty, address not found,
     *                           or any shop has a blocking validation error
     */
    @Transactional(readOnly = true)
    public CheckoutQuoteResponse generateQuote(String customerEmail, CheckoutQuoteRequest request) {
        User user = requireUser(customerEmail);

        // 1. Load the customer's cart
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> BusinessException.badRequest("Cart is empty"));
        List<CartItem> cartItems = cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId());
        if (cartItems.isEmpty()) {
            throw BusinessException.badRequest("Cart is empty");
        }

        // 2. Load and validate the delivery address (must belong to the caller)
        AddressRepository.AddressWithLocation address = addressRepository.findByIdAndUserIdWithLocation(request.getAddressId(), user.getId())
                .orElseThrow(() -> BusinessException.notFound("Address not found"));

        // 3. Group cart items by vendor
        Map<Long, List<CartItem>> itemsByVendor = cartItems.stream()
                .collect(Collectors.groupingBy(CartItem::getVendorId));

        // 4. Validate that request covers all vendors in the cart
        Set<Long> cartVendorIds = itemsByVendor.keySet();
        Set<Long> requestVendorIds = request.getItems().stream()
                .map(CheckoutQuoteRequest.QuoteItem::getVendorId)
                .collect(Collectors.toSet());

        if (!cartVendorIds.equals(requestVendorIds)) {
            throw BusinessException.badRequest("Slot selection must include exactly one entry per vendor in the cart");
        }

        // 5. Resolve platform fee from configuration (client cannot override)
        BigDecimal platformFee = checkoutProperties.getPlatformFee();

        // 6. Build per-shop quotes
        List<CheckoutShopQuote> shopQuotes = new ArrayList<>();
        boolean hasErrors = false;
        boolean hasPriceChanges = false;

        for (CheckoutQuoteRequest.QuoteItem quoteItem : request.getItems()) {
            Long vendorId = quoteItem.getVendorId();
            List<CartItem> vendorItems = itemsByVendor.get(vendorId);

            VendorProfile vendor = vendorProfileRepository.findById(vendorId)
                    .orElseThrow(() -> BusinessException.notFound("Vendor not found"));

            CheckoutShopQuote shopQuote = buildShopQuote(vendor, vendorItems, address, quoteItem.getSlotStart(), platformFee);
            shopQuotes.add(shopQuote);

            if (!shopQuote.getErrors().isEmpty()) {
                hasErrors = true;
            }
            if (shopQuote.getWarnings() != null && shopQuote.getWarnings().stream()
                    .anyMatch(w -> w.contains("price_changed"))) {
                hasPriceChanges = true;
            }
        }

        // 7. Compute grand total (only over shops without errors)
        BigDecimal grandTotal = shopQuotes.stream()
                .filter(s -> s.getErrors().isEmpty())
                .map(CheckoutShopQuote::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CheckoutQuoteResponse.builder()
                .shops(shopQuotes)
                .total(grandTotal.setScale(SCALE_2, ROUNDING))
                .hasErrors(hasErrors)
                .hasPriceChanges(hasPriceChanges)
                .quoteHash(computeQuoteHash(user.getId(), request.getAddressId(), cartItems, request.getItems(), shopQuotes, platformFee))
                .build();
    }

    /**
     * Computes an HMAC-SHA256 signature of all material quote inputs.
     * This binds the quote to the authenticated customer, address, cart items,
     * vendor slots, pricing, and configuration. The client must echo this
     * hash back in the checkout request to prove they confirmed this exact quote.
     */
    private String computeQuoteHash(Long userId, Long addressId, List<CartItem> cartItems,
                                    List<? extends CheckoutQuoteRequest.QuoteItem> slotItems,
                                    List<CheckoutShopQuote> shopQuotes, BigDecimal platformFee) {
        try {
            String payload = buildQuotePayload(userId, addressId, cartItems, slotItems, shopQuotes, platformFee);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    checkoutProperties.getQuoteSigningSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute quote hash", e);
        }
    }

    /**
     * Computes an HMAC-SHA256 fingerprint of the complete checkout payload.
     * Binds the idempotency key to user, address, items, slots, and quote hash.
     * Grand total is excluded: it is derived from the quote and not known before
     * order creation.
     */
    private String computeCheckoutFingerprint(Long userId, UUID clientOrderId, Long addressId,
                                              List<CheckoutRequest.CheckoutItem> checkoutItems,
                                              String quoteHash, Order.PaymentMethod paymentMethod) {
        try {
            String payload = buildCheckoutFingerprintPayload(userId, clientOrderId, addressId, checkoutItems, quoteHash,
                    paymentMethod);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    checkoutProperties.getQuoteSigningSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute checkout fingerprint", e);
        }
    }

    /**
     * Builds a canonical payload string for checkout fingerprint hashing.
     * Includes all stable inputs that identify this specific checkout request.
     */
    private String buildCheckoutFingerprintPayload(Long userId, UUID clientOrderId, Long addressId,
                                                   List<CheckoutRequest.CheckoutItem> checkoutItems,
                                                   String quoteHash, Order.PaymentMethod paymentMethod) {
        StringBuilder sb = new StringBuilder();
        sb.append("user:").append(userId).append("|");
        sb.append("clientOrderId:").append(clientOrderId).append("|");
        sb.append("address:").append(addressId).append("|");
        sb.append("quoteHash:").append(quoteHash).append("|");
        sb.append("paymentMethod:").append(paymentMethod != null ? paymentMethod.name() : "COD").append("|");
        sb.append("items:");

        List<CheckoutRequest.CheckoutItem> sortedItems = new ArrayList<>(checkoutItems);
        sortedItems.sort(Comparator.comparing(CheckoutRequest.CheckoutItem::getVendorId));

        for (CheckoutRequest.CheckoutItem item : sortedItems) {
            sb.append(item.getVendorId()).append(":")
              .append(item.getSlotStart()).append(";");
        }

        return sb.toString();
    }

    /**
     * Builds a canonical payload string for quote hashing.
     * Includes all material inputs that affect the quote outcome.
     */
    private String buildQuotePayload(Long userId, Long addressId, List<CartItem> cartItems,
                                     List<? extends CheckoutQuoteRequest.QuoteItem> slotItems,
                                     List<CheckoutShopQuote> shopQuotes, BigDecimal platformFee) {
        StringBuilder sb = new StringBuilder();
        sb.append("user:").append(userId).append("|");
        sb.append("address:").append(addressId).append("|");
        sb.append("platformFee:").append(platformFee.toPlainString()).append("|");
        sb.append("items:");

        // Sort cart items by product ID for deterministic ordering
        List<CartItem> sortedItems = new ArrayList<>(cartItems);
        sortedItems.sort(Comparator.comparing(item -> item.getProduct().getId()));

        for (CartItem item : sortedItems) {
            Product product = item.getProduct();
            sb.append(product.getId()).append(":")
              .append(item.getQuantity()).append(":")
              .append(item.getPriceSnapshot().toPlainString()).append(";");
        }
        sb.append("|slots:");

        // Sort slots by vendor ID for deterministic ordering
        List<CheckoutQuoteRequest.QuoteItem> sortedSlots = new ArrayList<>(slotItems);
        sortedSlots.sort(Comparator.comparing(CheckoutQuoteRequest.QuoteItem::getVendorId));

        for (CheckoutQuoteRequest.QuoteItem slot : sortedSlots) {
            sb.append(slot.getVendorId()).append(":").append(slot.getSlotStart()).append(";");
        }
        sb.append("|shops:");

        // Sort shops by vendor ID for deterministic ordering
        List<CheckoutShopQuote> sortedShops = new ArrayList<>(shopQuotes);
        sortedShops.sort(Comparator.comparing(CheckoutShopQuote::getVendorId));

        for (CheckoutShopQuote shop : sortedShops) {
            sb.append(shop.getVendorId()).append(":")
              .append(shop.getSubtotal().toPlainString()).append(":")
              .append(shop.getDeliveryFee().toPlainString()).append(":")
              .append(shop.getTotal().toPlainString()).append(";");
        }

        return sb.toString();
    }

    /**
     * Verifies that the provided quoteHash matches the recomputed hash
     * for the current authoritative state. This ensures the client confirmed
     * the exact quote they received and no material inputs have changed.
     */
    private void verifyQuoteHash(String clientHash, Long userId, Long addressId, List<CartItem> cartItems,
                                 List<? extends CheckoutQuoteRequest.QuoteItem> slotItems,
                                 List<CheckoutShopQuote> shopQuotes, BigDecimal platformFee) {
        String expectedHash = computeQuoteHash(userId, addressId, cartItems, slotItems, shopQuotes, platformFee);
        if (!MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8),
                clientHash.getBytes(StandardCharsets.UTF_8))) {
            throw BusinessException.conflict(
                    "Quote confirmation invalid or stale. Material quote inputs have changed. " +
                    "Please obtain a fresh quote and confirm the updated totals.");
        }
    }

    /**
     * Converts a byte array to a hexadecimal string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Places the order for the authenticated customer's cart.
     *
     * <p>This is a single database transaction covering all vendors in the
     * cart. The workflow is all-or-nothing: if any vendor fails validation,
     * the entire checkout is rolled back.
     *
     * <p>Steps:
     * <ol>
     *   <li>Idempotency check: create or fetch idempotency record with fingerprint verification.</li>
     *   <li>Load and revalidate the cart, address, and all items.</li>
     *   <li>Verify quote hash against current authoritative state.</li>
     *   <li>Lock inventory rows in ascending product-id order.</li>
     *   <li>Re-validate stock and all business rules under the lock.</li>
     *   <li>Reserve stock by incrementing {@code reserved_quantity}.</li>
     *   <li>Create one order per vendor with items, status history, and totals.</li>
     *   <li>Complete idempotency record with {@code completed_at}.</li>
     *   <li>Clear the customer's cart.</li>
     * </ol>
     *
     * @param customerEmail the caller's email (from JWT subject)
     * @param request       the checkout request with idempotency key and quote hash
     * @return the created orders or the idempotent replay result
     * @throws BusinessException for validation errors, conflicts, or insufficient stock
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CheckoutResponse checkout(String customerEmail, CheckoutRequest request) {
        User user = requireUser(customerEmail);

        // Enforce CUSTOMER role
        if (user.getRole() == null || !"CUSTOMER".equals(user.getRole().getName())) {
            throw BusinessException.forbidden("Only customers can checkout");
        }

        Order.PaymentMethod paymentMethod = request.getPaymentMethod();
        if (paymentMethod == null) {
            paymentMethod = Order.PaymentMethod.COD;
        }

        // 1. Compute checkout fingerprint from canonical inputs (before any DB reads)
        String checkoutFingerprint = computeCheckoutFingerprint(
                user.getId(), request.getClientOrderId(), request.getAddressId(),
                request.getItems(), request.getQuoteHash(), paymentMethod);

        // 2. Idempotency check: fetch or create idempotency record
        CheckoutIdempotency idempotencyRecord = handleIdempotency(user.getId(),
                request.getClientOrderId().toString(), checkoutFingerprint);

        if (idempotencyRecord.getCompletedAt() != null) {
            // This is a replay of a previously completed checkout
            return buildIdempotentResponse(user.getId(), request.getClientOrderId(), request.getItems(), paymentMethod);
        }

        // 3. Load the customer's cart
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> BusinessException.badRequest("Cart is empty"));
        List<CartItem> cartItems = cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId());
        if (cartItems.isEmpty()) {
            throw BusinessException.badRequest("Cart is empty");
        }

        // 4. Load and validate the delivery address (must belong to the caller)
        AddressRepository.AddressWithLocation address = addressRepository.findByIdAndUserIdWithLocation(request.getAddressId(), user.getId())
                .orElseThrow(() -> BusinessException.notFound("Address not found"));

        // 5. Group cart items by vendor
        Map<Long, List<CartItem>> itemsByVendor = cartItems.stream()
                .collect(Collectors.groupingBy(CartItem::getVendorId));

        // 6. Validate that request covers all vendors in the cart
        Set<Long> cartVendorIds = itemsByVendor.keySet();
        Set<Long> requestVendorIds = request.getItems().stream()
                .map(CheckoutRequest.CheckoutItem::getVendorId)
                .collect(Collectors.toSet());

        if (!cartVendorIds.equals(requestVendorIds)) {
            throw BusinessException.badRequest("Slot selection must include exactly one entry per vendor in the cart");
        }

        // Validate slotStart format upfront for all items
        for (CheckoutRequest.CheckoutItem item : request.getItems()) {
            slotValidationService.parseAndNormalize(item.getSlotStart());
        }

        // 7. Resolve platform fee from configuration (client cannot override)
        BigDecimal platformFee = checkoutProperties.getPlatformFee();

        // 8. Collect all product IDs and lock inventory in deterministic ascending order (deadlock prevention)
        List<Long> allProductIds = cartItems.stream()
                .map(item -> item.getProduct().getId())
                .distinct()
                .sorted()
                .toList();

        Map<Long, Inventory> lockedInventories = new LinkedHashMap<>();
        for (Long productId : allProductIds) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> BusinessException.insufficientStock("Product has no inventory record"));
            lockedInventories.put(productId, inventory);
        }

        // 9. Collect and lock all vendor profiles in deterministic ascending order (deadlock prevention)
        List<Long> allVendorIds = request.getItems().stream()
                .map(CheckoutRequest.CheckoutItem::getVendorId)
                .distinct()
                .sorted()
                .toList();

        Map<Long, VendorProfile> lockedVendors = new LinkedHashMap<>();
        for (Long vendorId : allVendorIds) {
            VendorProfile vendor = vendorProfileRepository.findByIdForUpdate(vendorId)
                    .orElseThrow(() -> BusinessException.notFound("Vendor not found"));
            lockedVendors.put(vendorId, vendor);
        }

        // Validate slot rules for all vendors under lock before verifying quote hash
        for (CheckoutRequest.CheckoutItem checkoutItem : request.getItems()) {
            VendorProfile vendor = lockedVendors.get(checkoutItem.getVendorId());
            slotValidationService.validateSlot(vendor, checkoutItem.getSlotStart());
        }

        // 10. Check price changes under lock (reject unconfirmed changes before verifying quote hash)
        for (CartItem cartItem : cartItems) {
            Product product = productRepository.findById(cartItem.getProduct().getId())
                    .orElseThrow(() -> BusinessException.notFound("Product not found"));
            boolean priceChanged = cartItem.getPriceSnapshot().compareTo(product.getBasePrice()) != 0;
            if (priceChanged && !request.isConfirmPriceChanges()) {
                throw BusinessException.conflict("Price has changed for product '" + product.getName() + "' (was " + cartItem.getPriceSnapshot() + ", now " + product.getBasePrice() + "). Explicit confirmation is required.");
            }
        }

        // 11. Re-validate quote hash against current state under locks
        List<CheckoutQuoteRequest.QuoteItem> quoteItems = request.getItems().stream()
                .map(item -> CheckoutQuoteRequest.QuoteItem.builder()
                        .vendorId(item.getVendorId())
                        .slotStart(item.getSlotStart())
                        .build())
                .toList();

        List<CheckoutShopQuote> verificationShopQuotes = new ArrayList<>();
        for (CheckoutRequest.CheckoutItem reqItem : request.getItems()) {
            Long vId = reqItem.getVendorId();
            List<CartItem> vItems = itemsByVendor.get(vId);
            VendorProfile v = lockedVendors.get(vId);
            CheckoutShopQuote sq = buildShopQuote(v, vItems, address, reqItem.getSlotStart(), platformFee);
            verificationShopQuotes.add(sq);
        }

        verifyQuoteHash(request.getQuoteHash(), user.getId(), request.getAddressId(), cartItems, quoteItems, verificationShopQuotes, platformFee);

        // 12. Apply confirmed price changes to cart items now that quote hash is verified
        if (request.isConfirmPriceChanges()) {
            for (CartItem cartItem : cartItems) {
                Product product = productRepository.findById(cartItem.getProduct().getId())
                        .orElseThrow(() -> BusinessException.notFound("Product not found"));
                if (cartItem.getPriceSnapshot().compareTo(product.getBasePrice()) != 0) {
                    cartItem.setPriceSnapshot(product.getBasePrice());
                    cartItemRepository.save(cartItem);
                }
            }
        }

        // 13. Process each vendor — reserve stock, check slot capacity, create orders
        List<CheckoutResponse.OrderSummary> orderSummaries = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        LocalDateTime now = LocalDateTime.now(clock);

        // Pre-compute the payment group and intent reference for ONLINE checkout
        // (single intent for the combined grand total). The IDs are known up
        // front so each order's Payment row can link to the group inside the
        // loop; the intent row itself is written once grandTotal is known.
        String paymentGroupId = null;
        String intentReference = null;
        LocalDateTime intentExpiresAt = null;
        if (paymentMethod == Order.PaymentMethod.ONLINE) {
            paymentGroupId = UUID.randomUUID().toString();
            intentReference = "intent_sim_" + UUID.randomUUID();
            intentExpiresAt = now.plusMinutes(appProperties.getIntentTtlMinutes());
        }

        for (CheckoutRequest.CheckoutItem checkoutItem : request.getItems()) {
            Long vendorId = checkoutItem.getVendorId();
            List<CartItem> vendorItems = itemsByVendor.get(vendorId);
            VendorProfile vendor = lockedVendors.get(vendorId);

            // Validate slot under vendor lock using shared SlotValidationService
            SlotValidationService.ValidatedSlot validatedSlot = slotValidationService.validateSlot(vendor, checkoutItem.getSlotStart());
            LocalDateTime slotStart = validatedSlot.slotStartUtc();
            LocalDateTime slotEnd = validatedSlot.slotEndUtc();

            // Slot capacity check under vendor lock. PENDING_PAYMENT orders
            // consume capacity (countBookedOrdersForSlot includes them) so an
            // unpaid order holds its slot until payment succeeds or is cancelled.
            long bookedInSlot = orderRepository.countBookedOrdersForSlot(vendor.getId(), slotStart);
            if (bookedInSlot >= vendor.getMaxOrdersPerSlot()) {
                throw BusinessException.slotFull("Delivery slot is full for vendor: " + vendor.getBusinessName());
            }

            // Vendor eligibility re-check
            if (vendor.getStatus() != VendorProfile.Status.APPROVED) {
                throw BusinessException.forbidden("Vendor is not approved");
            }
            if (!vendor.isAcceptingOrders()) {
                throw BusinessException.conflict("Vendor is not accepting orders");
            }

            // Distance and radius check
            BigDecimal distanceKm = haversineKm(
                    vendor.getLatitude(), vendor.getLongitude(),
                    address.getLatitude(), address.getLongitude()
            ).setScale(SCALE_4, ROUNDING);

            if (distanceKm.compareTo(vendor.getDeliveryRadiusKm()) > 0) {
                throw BusinessException.badRequest("Address is outside this vendor's delivery radius");
            }

            // Subtotal calculation and stock reservation
            BigDecimal subtotal = BigDecimal.ZERO;
            for (CartItem cartItem : vendorItems) {
                Long productId = cartItem.getProduct().getId();
                Product product = productRepository.findById(productId)
                        .orElseThrow(() -> BusinessException.notFound("Product not found"));
                Inventory inventory = lockedInventories.get(productId);

                if (product.getStatus() != ProductStatus.ACTIVE) {
                    throw BusinessException.conflict("Product is no longer available");
                }

                int available = inventory.getAvailable();
                if (available < cartItem.getQuantity()) {
                    throw BusinessException.insufficientStock(
                            "Only %d units of %s are available".formatted(available, product.getName()));
                }

                // Reserve stock only: quantity is untouched until the vendor
                // accepts after payment. reserved_quantity grows instead.
                inventory.setReservedQuantity(inventory.getReservedQuantity() + cartItem.getQuantity());
                inventoryRepository.saveAndFlush(inventory);

                BigDecimal priceSnapshot = cartItem.getPriceSnapshot();
                BigDecimal itemSubtotal = priceSnapshot.multiply(BigDecimal.valueOf(cartItem.getQuantity()))
                        .setScale(SCALE_2, ROUNDING);
                subtotal = subtotal.add(itemSubtotal);
            }

            // Minimum order check
            if (subtotal.compareTo(vendor.getMinOrderAmount()) < 0) {
                throw BusinessException.minimumOrder(
                        "Subtotal below vendor's minimum order of " + vendor.getMinOrderAmount());
            }

            // Delivery fee and shop total
            BigDecimal deliveryFee = calculateDeliveryFee(vendor, subtotal, distanceKm);
            BigDecimal shopTotal = subtotal.add(deliveryFee).add(platformFee).setScale(SCALE_2, ROUNDING);
            grandTotal = grandTotal.add(shopTotal);

            // Resolve deterministic clientOrderId for this order
            String orderClientOrderId = resolveOrderClientOrderId(request.getClientOrderId(), vendor.getId(), request.getItems().size());

            // Create the order. COD orders start PLACED; ONLINE orders start
            // PENDING_PAYMENT and stay there until the customer resumes/pays.
            Order.OrderStatus orderStatus;
            String historyReason;
            if (paymentMethod == Order.PaymentMethod.ONLINE) {
                orderStatus = Order.OrderStatus.PENDING_PAYMENT;
                historyReason = "Order placed (ONLINE) - pending payment";
            } else {
                orderStatus = Order.OrderStatus.PLACED;
                historyReason = "Order placed (COD)";
            }

            Order order = Order.builder()
                    .user(user)
                    .vendor(vendor)
                    .clientOrderId(orderClientOrderId)
                    .status(orderStatus)
                    .paymentMethod(paymentMethod)
                    .paymentStatus(Order.PaymentStatus.PENDING)
                    .subtotal(subtotal.setScale(SCALE_2, ROUNDING))
                    .deliveryFee(deliveryFee)
                    .platformFee(platformFee)
                    .total(shopTotal)
                    .commissionRate(vendor.getCommissionRate() != null ? vendor.getCommissionRate() : checkoutProperties.getDefaultCommissionRate())
                    .deliveryAddressLine1(address.getLine1())
                    .deliveryAddressLine2(address.getLine2())
                    .deliveryServiceLocation(address.getServiceLocation())
                    .deliveryLatitude(address.getLatitude())
                    .deliveryLongitude(address.getLongitude())
                    .slotStart(slotStart)
                    .slotEnd(slotEnd)
                    .build();
            orderRepository.saveAndFlush(order);

            // Publish OrderPlaced for COD orders (online orders publish when
            // payment confirmation moves PENDING_PAYMENT -> PLACED via
            // OrderStateMachine). Never notify for PENDING_PAYMENT itself.
            if (paymentMethod == Order.PaymentMethod.COD) {
                publishAfterCommit(new OrderPlaced(this, order.getId(), user.getId(),
                        vendor.getUser().getId(), paymentMethod, order.getTotal(),
                        now.atZone(clock.getZone()).toInstant()));
            }

            // Create one payment row per order inside the checkout transaction.
            // COD: pending COD payment. ONLINE: pending payment linked to the
            // shared group/intent. Any failure rolls back orders, payments and
            // reservations together.
            Payment payment;
            if (paymentMethod == Order.PaymentMethod.ONLINE) {
                payment = Payment.builder()
                        .order(order)
                        .method(com.flowerconnect.payment.domain.Payment.PaymentMethod.ONLINE)
                        .provider("simulated")
                        .providerReference(intentReference)
                        .amount(order.getTotal())
                        .status(com.flowerconnect.payment.domain.Payment.PaymentStatus.PENDING)
                        .paymentGroupId(paymentGroupId)
                        .build();
                payment = paymentRepository.saveAndFlush(payment);
            } else {
                payment = paymentService.createCodPayment(order, order.getTotal());
            }

            // Create order items and append StockMovement for each
            List<CheckoutResponse.OrderItemSummary> orderItemSummaries = new ArrayList<>();
            for (CartItem cartItem : vendorItems) {
                Product product = productRepository.findById(cartItem.getProduct().getId())
                        .orElseThrow(() -> BusinessException.notFound("Product not found"));
                OrderItem orderItem = OrderItem.builder()
                        .order(order)
                        .product(product)
                        .vendorId(vendorId)
                        .quantity(cartItem.getQuantity())
                        .priceSnapshot(cartItem.getPriceSnapshot())
                        .note(cartItem.getNote())
                        .build();
                orderItemRepository.saveAndFlush(orderItem);

                StockMovement movement = StockMovement.builder()
                        .product(product)
                        .movementType(StockMovement.MovementType.RESERVE)
                        .quantityDelta(cartItem.getQuantity())
                        .reason("Order placed (checkout)")
                        .referenceId(order.getId())
                        .referenceType("order")
                        .actor(user)
                        .build();
                stockMovementRepository.saveAndFlush(movement);

                BigDecimal itemSubtotal = cartItem.getPriceSnapshot().multiply(BigDecimal.valueOf(cartItem.getQuantity()))
                        .setScale(SCALE_2, ROUNDING);
                orderItemSummaries.add(CheckoutResponse.OrderItemSummary.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .quantity(cartItem.getQuantity())
                        .priceSnapshot(cartItem.getPriceSnapshot())
                        .subtotal(itemSubtotal)
                        .build());
            }

            // Create initial status history (null -> orderStatus)
            OrderStatusHistory history = OrderStatusHistory.builder()
                    .order(order)
                    .fromStatus(null)
                    .toStatus(orderStatus)
                    .actor(user)
                    .reason(historyReason)
                    .build();
            orderStatusHistoryRepository.saveAndFlush(history);

            orderSummaries.add(CheckoutResponse.OrderSummary.builder()
                    .orderId(order.getId())
                    .vendorId(vendorId)
                    .vendorName(vendor.getBusinessName())
                    .status(order.getStatus().name())
                    .paymentStatus(order.getPaymentStatus().name())
                    .paymentMethod(order.getPaymentMethod().name())
                    .subtotal(subtotal.setScale(SCALE_2, ROUNDING))
                    .deliveryFee(deliveryFee)
                    .platformFee(platformFee)
                    .total(shopTotal)
                    .slotStart(slotStart.toString())
                    .slotEnd(slotEnd.toString())
                    .items(orderItemSummaries)
                    .build());
        }

        // Write the single payment intent for the combined grand total (ONLINE only).
        // Written after grandTotal is known; all in the same transaction so a
        // later failure rolls it back along with orders, payments and stock.
        if (paymentMethod == Order.PaymentMethod.ONLINE) {
            PaymentIntent intent = PaymentIntent.builder()
                    .paymentGroupId(paymentGroupId)
                    .total(grandTotal.setScale(SCALE_2, ROUNDING))
                    .status(IntentStatus.CREATED)
                    .providerReference(intentReference)
                    .expiresAt(intentExpiresAt)
                    .build();
            paymentIntentRepository.saveAndFlush(intent);
        }

        // 13. Complete the idempotency record — only set after all orders are written
        idempotencyRecord.setCompletedAt(LocalDateTime.now(clock));
        idempotencyRepository.saveAndFlush(idempotencyRecord);

        // 14. Clear the cart
        cartItemRepository.deleteAllByCartId(cart.getId());

        log.info("Customer {} placed checkout with {} order(s), clientOrderId={}, method={}",
                customerEmail, orderSummaries.size(), request.getClientOrderId(), paymentMethod);

        return CheckoutResponse.builder()
                .clientOrderId(request.getClientOrderId())
                .idempotentReplay(false)
                .orders(orderSummaries)
                .total(grandTotal.setScale(SCALE_2, ROUNDING))
                .paymentMethod(paymentMethod.name())
                .paymentGroupId(paymentGroupId)
                .intentReference(intentReference)
                .intentExpiresAt(intentExpiresAt != null ? intentExpiresAt.toString() : null)
                .build();
    }

    /**
     * Resolves the client_order_id stored on the order row.
     * Single-vendor checkouts preserve the exact client-provided UUID string.
     * Multi-vendor checkouts derive a deterministic RFC 4122 UUID per vendor
     * to satisfy the {@code (user_id, client_order_id)} database uniqueness constraint.
     */
    private String resolveOrderClientOrderId(UUID clientOrderId, Long vendorId, int totalVendors) {
        if (totalVendors <= 1) {
            return clientOrderId.toString();
        }
        return UUID.nameUUIDFromBytes((clientOrderId.toString() + ":" + vendorId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * Handles idempotency for a checkout attempt.
     */
    private CheckoutIdempotency handleIdempotency(Long userId, String clientOrderId,
                                                   String checkoutFingerprint) {
        // 1. Check if record already exists — fast read path for idempotent replays
        Optional<CheckoutIdempotency> existingOpt = idempotencyRepository.findByUserIdAndClientOrderId(userId, clientOrderId);
        if (existingOpt.isPresent()) {
            CheckoutIdempotency existing = existingOpt.get();
            if (existing.getCompletedAt() == null) {
                // A checkout with the same key is currently in progress
                throw BusinessException.conflict(
                        "A checkout with this clientOrderId is already in progress. Please wait and retry.");
            }

            // Completed — verify fingerprint using constant-time comparison to prevent timing attacks
            if (!MessageDigest.isEqual(
                    existing.getCheckoutFingerprint().getBytes(StandardCharsets.UTF_8),
                    checkoutFingerprint.getBytes(StandardCharsets.UTF_8))) {
                throw BusinessException.conflict(
                        "A checkout with this clientOrderId was already completed with a different payload. " +
                        "Use a new clientOrderId for a different order.");
            }

            return existing;
        }

        // 2. Not present — attempt to insert pending record in current transaction
        try {
            CheckoutIdempotency record = CheckoutIdempotency.builder()
                    .userId(userId)
                    .clientOrderId(clientOrderId)
                    .checkoutFingerprint(checkoutFingerprint)
                    .createdAt(LocalDateTime.now(clock))
                    .completedAt(null)
                    .build();
            return idempotencyRepository.saveAndFlush(record);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Fired if a concurrent transaction inserted the same (userId, clientOrderId) simultaneously
            throw BusinessException.conflict(
                    "A checkout with this clientOrderId is already in progress. Please wait and retry.");
        }
    }

    /**
     * Builds an idempotent response for a previously completed checkout.
     */
    private CheckoutResponse buildIdempotentResponse(Long userId, UUID clientOrderId,
                                                     List<CheckoutRequest.CheckoutItem> items,
                                                     Order.PaymentMethod paymentMethod) {
        List<Order> existingOrders = orderRepository.findByUserIdAndClientOrderId(userId, clientOrderId.toString());
        if (existingOrders.isEmpty() && items != null) {
            existingOrders = new ArrayList<>();
            for (CheckoutRequest.CheckoutItem item : items) {
                String derivedKey = resolveOrderClientOrderId(clientOrderId, item.getVendorId(), items.size());
                existingOrders.addAll(orderRepository.findByUserIdAndClientOrderId(userId, derivedKey));
            }
        }
        if (existingOrders.isEmpty()) {
            throw BusinessException.conflict("Duplicate checkout request detected but original orders not found");
        }

        // For ONLINE replays, recover the shared payment group/intent reference
        // from the original orders' payment rows so the client can resume.
        String paymentGroupId = null;
        String intentReference = null;
        String intentExpiresAt = null;
        if (paymentMethod == Order.PaymentMethod.ONLINE && !existingOrders.isEmpty()) {
            Payment firstPayment = paymentRepository.findByOrderId(existingOrders.get(0).getId())
                    .orElseThrow(() -> new IllegalStateException("Original ONLINE payment not found"));
            paymentGroupId = firstPayment.getPaymentGroupId();
            intentReference = firstPayment.getProviderReference();
            PaymentIntent intent = paymentIntentRepository.findByPaymentGroupId(paymentGroupId)
                    .orElseThrow(() -> new IllegalStateException("Original payment intent not found"));
            intentExpiresAt = intent.getExpiresAt() != null ? intent.getExpiresAt().toString() : null;
        }

        List<CheckoutResponse.OrderSummary> summaries = existingOrders.stream()
                .map(order -> {
                    List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
                    List<CheckoutResponse.OrderItemSummary> itemSummaries = orderItems.stream()
                            .map(item -> CheckoutResponse.OrderItemSummary.builder()
                                    .productId(item.getProduct().getId())
                                    .productName(item.getProduct().getName())
                                    .quantity(item.getQuantity())
                                    .priceSnapshot(item.getPriceSnapshot())
                                    .subtotal(item.getPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())).setScale(SCALE_2, ROUNDING))
                                    .build())
                            .toList();

                    return CheckoutResponse.OrderSummary.builder()
                            .orderId(order.getId())
                            .vendorId(order.getVendor().getId())
                            .vendorName(order.getVendor().getBusinessName())
                            .status(order.getStatus().name())
                            .paymentStatus(order.getPaymentStatus().name())
                            .paymentMethod(order.getPaymentMethod().name())
                            .subtotal(order.getSubtotal())
                            .deliveryFee(order.getDeliveryFee())
                            .platformFee(order.getPlatformFee())
                            .total(order.getTotal())
                            .slotStart(order.getSlotStart() != null ? order.getSlotStart().toString() : null)
                            .slotEnd(order.getSlotEnd() != null ? order.getSlotEnd().toString() : null)
                            .items(itemSummaries)
                            .build();
                })
                .toList();

        BigDecimal total = summaries.stream()
                .map(CheckoutResponse.OrderSummary::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CheckoutResponse.builder()
                .clientOrderId(clientOrderId)
                .idempotentReplay(true)
                .orders(summaries)
                .total(total.setScale(SCALE_2, ROUNDING))
                .paymentMethod(paymentMethod != null ? paymentMethod.name() : null)
                .paymentGroupId(paymentGroupId)
                .intentReference(intentReference)
                .intentExpiresAt(intentExpiresAt)
                .build();
    }


    /**
     * Builds the quote for a single vendor's items.
     */
    private CheckoutShopQuote buildShopQuote(VendorProfile vendor,
                                             List<CartItem> vendorItems,
                                             AddressRepository.AddressWithLocation address,
                                             String slotStartStr,
                                             BigDecimal platformFee) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // ---- Vendor eligibility ----
        if (vendor.getStatus() != VendorProfile.Status.APPROVED) {
            errors.add("Vendor is not approved");
        }
        if (!vendor.isAcceptingOrders()) {
            errors.add("Vendor is not accepting orders");
        }

        // ---- Distance and radius check ----
        BigDecimal distanceKm = haversineKm(
                vendor.getLatitude(), vendor.getLongitude(),
                address.getLatitude(), address.getLongitude()
        ).setScale(SCALE_4, ROUNDING);

        boolean withinRadius = distanceKm.compareTo(vendor.getDeliveryRadiusKm()) <= 0;
        if (!withinRadius) {
            errors.add("Address is outside this vendor's delivery radius");
        }

        // ---- Build item quotes ----
        List<CheckoutItemQuote> itemQuotes = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        boolean allItemsPurchasable = true;

        for (CartItem cartItem : vendorItems) {
            Product product = productRepository.findById(cartItem.getProduct().getId())
                    .orElse(null);
            Inventory inventory = inventoryRepository.findByProductId(product != null ? product.getId() : -1L)
                    .orElse(null);

            CheckoutItemQuote itemQuote = buildItemQuote(cartItem, product, inventory);
            itemQuotes.add(itemQuote);

            subtotal = subtotal.add(itemQuote.getSubtotal());

            if (!itemQuote.isPurchasable()) {
                allItemsPurchasable = false;
            }
            if (itemQuote.isPriceChanged()) {
                warnings.add("price_changed_since_add_to_cart");
            }
        }

        // ---- Delivery fee ----
        BigDecimal deliveryFee = calculateDeliveryFee(vendor, subtotal, distanceKm);

        // ---- Minimum order check ----
        boolean minOrderMet = subtotal.compareTo(vendor.getMinOrderAmount()) >= 0;
        if (!minOrderMet) {
            errors.add("Subtotal below vendor's minimum order of " + vendor.getMinOrderAmount());
        }

        // ---- Slot validation ----
        boolean slotAvailable = true;
        try {
            SlotValidationService.ValidatedSlot validatedSlot = slotValidationService.validateSlot(vendor, slotStartStr);
            long bookedInSlot = orderRepository.countBookedOrdersForSlot(vendor.getId(), validatedSlot.slotStartUtc());
            if (bookedInSlot >= vendor.getMaxOrdersPerSlot()) {
                slotAvailable = false;
                errors.add("Delivery slot is full for vendor: " + vendor.getBusinessName());
            }
        } catch (BusinessException e) {
            slotAvailable = false;
            errors.add(e.getMessage());
        }

        // ---- Shop total ----
        BigDecimal total = subtotal.add(deliveryFee).add(platformFee).setScale(SCALE_2, ROUNDING);

        return CheckoutShopQuote.builder()
                .vendorId(vendor.getId())
                .vendorName(vendor.getBusinessName())
                .items(itemQuotes)
                .subtotal(subtotal.setScale(SCALE_2, ROUNDING))
                .deliveryFee(deliveryFee)
                .platformFee(platformFee)
                .minOrderAmount(vendor.getMinOrderAmount())
                .total(total)
                .distanceKm(distanceKm.setScale(SCALE_2, ROUNDING))
                .withinRadius(withinRadius)
                .minOrderMet(minOrderMet)
                .slotAvailable(slotAvailable)
                .warnings(warnings.isEmpty() ? List.of() : warnings)
                .errors(errors.isEmpty() ? List.of() : errors)
                .build();
    }

    /**
     * Builds the item quote with price snapshot comparison.
     */
    private CheckoutItemQuote buildItemQuote(CartItem cartItem, Product product, Inventory inventory) {
        BigDecimal priceSnapshot = cartItem.getPriceSnapshot();
        BigDecimal currentPrice = product != null ? product.getBasePrice() : null;
        boolean priceChanged = currentPrice != null && priceSnapshot.compareTo(currentPrice) != 0;

        int available = inventory != null ? inventory.getAvailable() : 0;
        BigDecimal subtotal = priceSnapshot.multiply(BigDecimal.valueOf(cartItem.getQuantity()))
                .setScale(SCALE_2, ROUNDING);

        boolean purchasable = true;
        String reason = null;

        if (product == null || product.getStatus() != ProductStatus.ACTIVE) {
            purchasable = false;
            reason = "Product is no longer available";
        } else if (available < cartItem.getQuantity()) {
            purchasable = false;
            reason = "Insufficient stock";
        }

        return CheckoutItemQuote.builder()
                .productId(cartItem.getProduct().getId())
                .productName(product != null ? product.getName() : null)
                .productSlug(product != null ? product.getSlug() : null)
                .quantity(cartItem.getQuantity())
                .priceSnapshot(priceSnapshot)
                .currentPrice(currentPrice)
                .priceChanged(priceChanged)
                .note(cartItem.getNote())
                .subtotal(subtotal)
                .available(available)
                .purchasable(purchasable)
                .notPurchasableReason(reason)
                .build();
    }

    /**
     * Calculates the delivery fee for a vendor.
     * Formula: baseFee + perKmFee × distanceKm, or 0 if subtotal ≥ freeDeliveryAbove.
     */
    private BigDecimal calculateDeliveryFee(VendorProfile vendor, BigDecimal subtotal, BigDecimal distanceKm) {
        if (vendor.getFreeDeliveryAbove() != null &&
                subtotal.compareTo(vendor.getFreeDeliveryAbove()) >= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal baseFee = vendor.getBaseDeliveryFee();
        BigDecimal perKm = vendor.getPerKmFee();
        BigDecimal fee = baseFee.add(perKm.multiply(distanceKm))
                .setScale(SCALE_2, ROUNDING);
        return fee.max(BigDecimal.ZERO); // never negative
    }

    /**
     * Haversine formula: distance in kilometres between two lat/lng points.
     * Copied from DiscoveryService to keep quote self-contained.
     */
    private BigDecimal haversineKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double lat1Rad = Math.toRadians(lat1.doubleValue());
        double lng1Rad = Math.toRadians(lng1.doubleValue());
        double lat2Rad = Math.toRadians(lat2.doubleValue());
        double lng2Rad = Math.toRadians(lng2.doubleValue());

        double dLat = lat2Rad - lat1Rad;
        double dLng = lng2Rad - lng1Rad;

        double sinDLat = Math.sin(dLat / 2);
        double sinDLng = Math.sin(dLng / 2);

        double a = sinDLat * sinDLat +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) * sinDLng * sinDLng;

        double c = 2 * Math.asin(Math.min(1.0, Math.sqrt(a)));
        double distance = EARTH_RADIUS_KM.doubleValue() * c;

        return BigDecimal.valueOf(distance);
    }

    private User requireUser(String customerEmail) {
        return userRepository.findByEmailWithRole(customerEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    /**
     * Publishes a scalar-only domain event after the current transaction
     * commits, mirroring the pattern in {@code OrderStateMachine}. A rollback
     * of the checkout transaction prevents the event from ever being
     * published.
     */
    private void publishAfterCommit(ApplicationEvent event) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(
                            new org.springframework.transaction.support.TransactionSynchronizationAdapter() {
                                @Override
                                public void afterCommit() {
                                    eventPublisher.publishEvent(event);
                                }
                            });
        } else {
            eventPublisher.publishEvent(event);
        }
    }
}