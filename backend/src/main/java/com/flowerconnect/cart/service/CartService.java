package com.flowerconnect.cart.service;

import com.flowerconnect.cart.domain.Cart;
import com.flowerconnect.cart.domain.CartItem;
import com.flowerconnect.cart.dto.*;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.cart.repository.CartItemRepository;
import com.flowerconnect.cart.repository.CartRepository;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Customer cart service (plan task 5.2).
 *
 * <p><b>Ownership.</b> Every method resolves the caller from the JWT subject
 * (the email on the authentication), never from a request parameter. The cart
 * is fetched by {@code user_id}, so a customer can only access their own cart.
 *
 * <p><b>Eligibility on add.</b> Adding a product checks four things in order
 * (plan §2.3):
 * <ol>
 *   <li>Product exists and is {@code ACTIVE} — non-active is 404 (not visible
 *       to the customer in discovery, so a direct id reference is "not found").</li>
 *   <li>Vendor is {@code APPROVED} — a {@code PENDING_APPROVAL} or
 *       {@code SUSPENDED} vendor is 403 {@code FORBIDDEN} (the customer can see
 *       the product in discovery but cannot order from an unapproved vendor).</li>
 *   <li>Vendor is {@code acceptingOrders} — false is 409 {@code CONFLICT}.</li>
 *   <li>Sufficient available stock — not enough is 409
 *       {@code INSUFFICIENT_STOCK}.</li>
 * </ol>
 *
 * <p><b>No stock reservation.</b> Cart items do not reserve inventory — the
 * stock is only checked, never decremented or reserved, so a cart abandonment
 * does not tie up stock. The reservation happens at checkout (task 5.4) under
 * the inventory lock.
 *
 * <p><b>Add vs. increment.</b> Adding a product already in the cart increments
 * its quantity (the {@code uq_cart_item_cart_product} constraint enforces one
 * line per product); setting quantity to 0 via PATCH deletes the line.
 *
 * <p><b>Price snapshot.</b> The {@code price_snapshot} on the cart item is taken
 * from {@code products.base_price} at add time (decision C-1). It is the
 * authority for the cart subtotal; the current price is returned alongside so
 * the customer can see drift before the quote re-validates it.
 *
 * <p><b>Stale-cart read.</b> On {@code GET}, an item whose product was
 * deactivated or whose stock ran out after being added is still shown — the
 * snapshot and quantity are still valid information — but {@code purchasable}
 * is false with a reason, so the quote/checkout path can refuse it explicitly.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;

    /**
     * Retrieves the caller's cart, grouped by vendor with per-item
     * eligibility flags. If the customer has no cart yet, an empty cart
     * response is returned — the cart is created lazily on the first
     * add-to-cart.
     */
    @Transactional(readOnly = true)
    public CartResponse getCart(String customerEmail) {
        User user = requireUser(customerEmail);
        Optional<Cart> cartOpt = cartRepository.findByUserId(user.getId());

        if (cartOpt.isEmpty() || cartItemRepository.countByCartId(cartOpt.get().getId()) == 0) {
            return cartOpt
                    .map(cart -> emptyCartResponse(cart.getId()))
                    .orElseGet(this::emptyCartResponse);
        }

        Cart cart = cartOpt.get();
        List<CartItem> items = cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId());
        return buildCartResponse(cart.getId(), items);
    }

    /**
     * Adds a product to the caller's cart.
     *
     * <p>Eligibility is validated against the product, vendor, and inventory
     * (stock is read-only — no reservation). If the product is already in the
     * cart the quantity is incremented; otherwise a new line is created with
     * the current {@code base_price} as the snapshot.
     */
    public CartItemResponse addItem(String customerEmail, AddCartItemRequest request) {
        User user = requireUser(customerEmail);
        Product product = requirePurchasableProduct(request.getProductId());

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    Cart newCart = Cart.builder().user(user).build();
                    cartRepository.saveAndFlush(newCart);
                    log.info("Created cart {} for customer {}", newCart.getId(), user.getEmail());
                    return newCart;
                });

        Optional<CartItem> existing = cartItemRepository.findByCartIdAndProductId(cart.getId(), request.getProductId());
        CartItem item;

        if (existing.isPresent()) {
            item = existing.get();
            int newQuantity = item.getQuantity() + request.getQuantity();
            checkStockAvailable(product, newQuantity);
            item.setQuantity(newQuantity);
            if (request.getNote() != null) {
                item.setNote(request.getNote());
            }
            cartItemRepository.saveAndFlush(item);
            log.info("Incremented product {} in cart {} to {}", product.getId(), cart.getId(), newQuantity);
        } else {
            checkStockAvailable(product, request.getQuantity());
            item = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .vendorId(product.getVendor().getId())
                    .quantity(request.getQuantity())
                    .priceSnapshot(product.getBasePrice())
                    .note(request.getNote())
                    .build();
            cartItemRepository.saveAndFlush(item);
            log.info("Added product {} (qty {}) to cart {}", product.getId(), request.getQuantity(), cart.getId());
        }

        return toItemResponse(item, product, item.getVendorId());
    }

    /**
     * Updates a cart item's quantity. A quantity of zero deletes the line.
     * A quantity increase is re-checked against current available stock.
     */
    public CartItemResponse updateItem(String customerEmail, Long cartItemId, UpdateCartItemRequest request) {
        User user = requireUser(customerEmail);
        CartItem item = requireOwnedCartItem(user.getId(), cartItemId);

        if (request.getQuantity() == 0) {
            cartItemRepository.delete(item);
            log.info("Removed cart item {} (quantity set to 0)", cartItemId);
            return null;
        }

        Product product = productRepository.findById(item.getProduct().getId())
                .orElseThrow(() -> BusinessException.notFound("Product not found"));
        checkStockAvailable(product, request.getQuantity());

        item.setQuantity(request.getQuantity());
        cartItemRepository.saveAndFlush(item);
        log.info("Updated cart item {} to quantity {}", cartItemId, request.getQuantity());

        return toItemResponse(item, product, item.getVendorId());
    }

    /**
     * Removes a single line item from the caller's cart.
     */
    public void removeItem(String customerEmail, Long cartItemId) {
        User user = requireUser(customerEmail);
        CartItem item = requireOwnedCartItem(user.getId(), cartItemId);
        cartItemRepository.delete(item);
        log.info("Removed cart item {}", cartItemId);
    }

    /**
     * Deletes every line item from the caller's cart. The {@code carts} row
     * itself is retained (empty) so the next add-to-cart is a simple insert.
     */
    public void clearCart(String customerEmail) {
        User user = requireUser(customerEmail);
        cartRepository.findByUserId(user.getId()).ifPresentOrElse(
                cart -> {
                    cartItemRepository.deleteAllByCartId(cart.getId());
                    log.info("Cleared cart {}", cart.getId());
                },
                () -> log.info("Customer {} has no cart to clear", customerEmail)
        );
    }

    // ------------------------------------------------------------------
    // Lookups and ownership
    // ------------------------------------------------------------------

    private User requireUser(String customerEmail) {
        return userRepository.findByEmailWithRole(customerEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    /**
     * Resolves a product for cart-add: it must exist and be ACTIVE (hidden
     * products return 404), and its vendor must be APPROVED and accepting
     * orders. A non-approved vendor is 403 FORBIDDEN; a non-accepting vendor
     * is 409 CONFLICT. This mirrors the {@code DiscoveryService} filter
     * (approved + accepting) so a customer cannot add what discovery could
     * not have shown them.
     */
    private Product requirePurchasableProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.notFound("Product not found"));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw BusinessException.notFound("Product not found");
        }

        VendorProfile vendor = vendorProfileRepository.findById(product.getVendor().getId())
                .orElseThrow(() -> BusinessException.notFound("Vendor not found"));

        if (vendor.getStatus() != VendorProfile.Status.APPROVED) {
            throw BusinessException.forbidden("Vendor is not approved");
        }

        if (!vendor.isAcceptingOrders()) {
            throw BusinessException.conflict("Vendor is not accepting orders");
        }

        return product;
    }

    /**
     * The customer's own cart item by id, or 404. Because the lookup is scoped
     * by the owning user, an item belonging to another customer and a
     * nonexistent item are the same empty result.
     */
    private CartItem requireOwnedCartItem(Long userId, Long cartItemId) {
        return cartItemRepository.findById(cartItemId)
                .filter(item -> item.getCart().getUser().getId().equals(userId))
                .orElseThrow(() -> BusinessException.notFound("Cart item not found"));
    }

    /**
     * Validates that the requested quantity does not exceed available stock.
     * Stock is read-only here — no reservation is made.
     */
    private void checkStockAvailable(Product product, int requestedQuantity) {
        Inventory inventory = inventoryRepository.findByProductId(product.getId())
                .orElseThrow(() -> BusinessException.insufficientStock(
                        "Product has no inventory record"));
        int available = inventory.getAvailable();
        if (available < requestedQuantity) {
            throw BusinessException.insufficientStock(
                    "Only %d units of %s are available".formatted(available, product.getName()));
        }
    }

    // ------------------------------------------------------------------
    // Response building
    // ------------------------------------------------------------------

    /**
     * Builds a single {@link CartItemResponse} after a save, fetching the
     * vendor profile and inventory in the same transaction so the response
     * reflects the current state.
     */
    private CartItemResponse toItemResponse(CartItem item, Product product, Long vendorIdKey) {
        VendorProfile vendor = vendorProfileRepository.findById(vendorIdKey).orElse(null);
        Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElse(null);
        return toResponse(item, product, vendor, inventory);
    }

    private CartResponse buildCartResponse(Long cartId, List<CartItem> items) {
        Set<Long> productIds = items.stream()
                .map(item -> item.getProduct().getId())
                .collect(Collectors.toSet());
        Set<Long> vendorIds = items.stream()
                .map(CartItem::getVendorId)
                .collect(Collectors.toSet());

        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        Map<Long, VendorProfile> vendors = vendorProfileRepository.findAllById(vendorIds).stream()
                .collect(Collectors.toMap(VendorProfile::getId, v -> v));
        Map<Long, Inventory> inventories = inventoryRepository.findAllByProductIdIn(new ArrayList<>(productIds))
                .stream().collect(Collectors.toMap(inv -> inv.getProduct().getId(), inv -> inv));

        List<CartItemResponse> itemResponses = items.stream()
                .map(item -> {
                    Product product = products.get(item.getProduct().getId());
                    VendorProfile vendor = vendors.get(item.getVendorId());
                    Inventory inventory = inventories.get(item.getProduct().getId());
                    return toResponse(item, product, vendor, inventory);
                })
                .toList();

        BigDecimal subtotal = itemResponses.stream()
                .map(CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, List<CartItemResponse>> byVendor = itemResponses.stream()
                .collect(Collectors.groupingBy(CartItemResponse::getVendorId));

        List<CartShopView> shops = byVendor.entrySet().stream()
                .map(entry -> {
                    List<CartItemResponse> vendorItems = entry.getValue();
                    BigDecimal vendorSubtotal = vendorItems.stream()
                            .map(CartItemResponse::getSubtotal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    boolean purchasable = vendorItems.stream().allMatch(CartItemResponse::isPurchasable);
                    VendorProfile vp = vendors.get(entry.getKey());
                    return CartShopView.builder()
                            .vendorId(entry.getKey())
                            .vendorName(vp != null ? vp.getBusinessName() : null)
                            .itemCount(vendorItems.size())
                            .subtotal(vendorSubtotal)
                            .purchasable(purchasable)
                            .build();
                })
                .sorted(Comparator.comparing(CartShopView::getVendorId))
                .toList();

        return CartResponse.builder()
                .cartId(cartId)
                .totalItems(items.size())
                .subtotal(subtotal)
                .shops(shops)
                .items(itemResponses)
                .build();
    }

    private CartItemResponse toResponse(CartItem item, Product product, VendorProfile vendor, Inventory inventory) {
        BigDecimal priceSnapshot = item.getPriceSnapshot();
        BigDecimal currentPrice = product != null ? product.getBasePrice() : null;
        boolean priceChanged = currentPrice != null && priceSnapshot.compareTo(currentPrice) != 0;
        BigDecimal subtotal = priceSnapshot.multiply(BigDecimal.valueOf(item.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);

        int available = inventory != null ? inventory.getAvailable() : 0;
        boolean purchasable = true;
        String reason = null;

        if (product == null || product.getStatus() != ProductStatus.ACTIVE) {
            purchasable = false;
            reason = "Product is no longer available";
        } else if (vendor == null) {
            purchasable = false;
            reason = "Vendor is no longer available";
        } else if (vendor.getStatus() != VendorProfile.Status.APPROVED) {
            purchasable = false;
            reason = "Vendor is not approved";
        } else if (!vendor.isAcceptingOrders()) {
            purchasable = false;
            reason = "Vendor is not accepting orders";
        } else if (inventory == null || available < item.getQuantity()) {
            purchasable = false;
            reason = "Insufficient stock";
        }

        return CartItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(product != null ? product.getName() : null)
                .productSlug(product != null ? product.getSlug() : null)
                .vendorId(item.getVendorId())
                .vendorName(vendor != null ? vendor.getBusinessName() : null)
                .quantity(item.getQuantity())
                .priceSnapshot(priceSnapshot)
                .note(item.getNote())
                .subtotal(subtotal)
                .currentPrice(currentPrice)
                .priceChanged(priceChanged)
                .available(available)
                .purchasable(purchasable)
                .notPurchasableReason(reason)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private CartResponse emptyCartResponse() {
        return CartResponse.builder()
                .totalItems(0)
                .subtotal(BigDecimal.ZERO)
                .shops(List.of())
                .items(List.of())
                .build();
    }

    private CartResponse emptyCartResponse(Long cartId) {
        CartResponse response = emptyCartResponse();
        response.setCartId(cartId);
        return response;
    }
}
