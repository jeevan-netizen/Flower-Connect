package com.flowerconnect.order.service;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.inventory.service.InventoryService;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.domain.OrderItem;
import com.flowerconnect.order.domain.OrderStatusHistory;
import com.flowerconnect.order.event.OrderCancelled;
import com.flowerconnect.order.event.OrderDelivered;
import com.flowerconnect.order.event.OrderStatusChanged;
import com.flowerconnect.order.repository.OrderItemRepository;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.repository.OrderStatusHistoryRepository;
import com.flowerconnect.payment.service.PaymentService;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.inventory.domain.StockDelta;
import com.flowerconnect.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exhaustive unit test for {@link OrderStateMachine}.
 *
 * <p>The transition table itself is read from {@link OrderStateMachine#TRANSITIONS}, so the test
 * can never drift from the implementation's declared section 6.1 table. Every {@code (from, to,
 * actor)} combination across the full status and actor enums is exercised: allowed combinations
 * succeed and write history + events; every disallowed combination returns 409 CONFLICT with no
 * mutations.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderStateMachineTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PaymentService paymentService;
    @Mock
    private com.flowerconnect.billing.service.CommissionService commissionService;
    @Mock
    private java.time.Clock clock;
    @Mock
    private Product product;
    @Mock
    private OrderItem orderItem;

    @InjectMocks
    private OrderStateMachine stateMachine;

    private User customerUser;
    private User vendorUser;
    private User adminUser;
    private Order order;
    private Instant fixedInstant;

    @BeforeEach
    void setUp() {
        Role customerRole = Role.builder().id(1L).name("CUSTOMER").build();
        customerUser = User.builder()
                .id(10L).email("customer@test.com").role(customerRole)
                .status(User.Status.ACTIVE).build();
        Role vendorRole = Role.builder().id(2L).name("FLORIST").build();
        vendorUser = User.builder()
                .id(11L).email("vendor@test.com").role(vendorRole)
                .status(User.Status.ACTIVE).build();
        Role adminRole = Role.builder().id(3L).name("ADMIN").build();
        adminUser = User.builder()
                .id(12L).email("admin@test.com").role(adminRole)
                .status(User.Status.ACTIVE).build();

        fixedInstant = Instant.parse("2026-10-20T10:00:00Z");
        when(clock.instant()).thenReturn(fixedInstant);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        when(product.getId()).thenReturn(20L);
        when(orderItem.getProduct()).thenReturn(product);
        when(orderItem.getQuantity()).thenReturn(2);

        order = Order.builder()
                .id(1L)
                .user(customerUser)
                .vendor(null)
                .status(OrderStatus.PLACED)
                .paymentMethod(Order.PaymentMethod.COD)
                .paymentStatus(Order.PaymentStatus.PENDING)
                .subtotal(new BigDecimal("100.00"))
                .deliveryFee(BigDecimal.ZERO)
                .platformFee(BigDecimal.ZERO)
                .total(new BigDecimal("100.00"))
                .build();

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(userRepository.findById(10L)).thenReturn(Optional.of(customerUser));
        when(userRepository.findById(11L)).thenReturn(Optional.of(vendorUser));
        when(userRepository.findById(12L)).thenReturn(Optional.of(adminUser));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(orderItem));
        when(paymentService.collectPayment(any(Long.class))).thenAnswer(inv -> {
            order.setPaymentStatus(Order.PaymentStatus.COLLECTED);
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Exhaustive table-driven test (uses the implementation's table)
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] {0} -> {1} by {2}")
    @MethodSource("allTransitions")
    void transitionTable(OrderStatus from, OrderStatus to, ActorType actor) {
        order.setStatus(from);
        clearInvocations(orderRepository, orderStatusHistoryRepository, inventoryService,
                eventPublisher, userRepository);

        OrderStateMachine.TransitionKey key = new OrderStateMachine.TransitionKey(from, to, actor);
        OrderStateMachine.Transition transition = OrderStateMachine.TRANSITIONS.get(key);
        boolean allowed = transition != null;

        Long actorUserId = actorUserId(actor);
        String reason = allowed && transition.reasonRequired() ? "test reason" : null;

        if (!allowed) {
            BusinessException ex = null;
            try {
                stateMachine.transition(1L, to, actor, actorUserId, reason);
                throw new AssertionError("Expected BusinessException for " + from + " -> " + to + " by " + actor);
            } catch (BusinessException e) {
                ex = e;
            }
            assertThat(ex).isNotNull()
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT);
            String msg = ex.getMessage();
            assertThat(msg).satisfiesAnyOf(
                    m -> m.startsWith("Illegal transition " + from + " -> " + to),
                    m -> m.startsWith("Order 1 is already in terminal state " + from));
            // No mutations for an illegal transition.
            verify(orderRepository, never()).saveAndFlush(any());
            verify(orderStatusHistoryRepository, never()).saveAndFlush(any());
            verify(eventPublisher, never()).publishEvent(any());
            return;
        }

        // Allowed transition.
        Order result = stateMachine.transition(1L, to, actor, actorUserId, reason);

        assertThat(result).isSameAs(order);
        assertThat(order.getStatus()).isEqualTo(to);

        // Exactly one history row, with the declared fields and clock timestamp.
        verify(orderRepository).saveAndFlush(order);
        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository, times(1)).saveAndFlush(historyCaptor.capture());
        OrderStatusHistory history = historyCaptor.getValue();
        assertThat(history.getFromStatus()).isEqualTo(from);
        assertThat(history.getToStatus()).isEqualTo(to);
        assertThat(history.getActorType()).isEqualTo(actor);
        assertThat(history.getActor()).isEqualTo(actorFor(actor));
        assertThat(history.getReason()).isEqualTo(normalize(reason));
        assertThat(history.getCreatedAt()).isEqualTo(LocalDateTime.now(clock));

        // Scalar-only event published for every transition, plus cancellation/delivery events.
        ArgumentCaptor<ApplicationEvent> eventCaptor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());
        List<ApplicationEvent> events = eventCaptor.getAllValues();
        assertThat(events).anyMatch(e -> e instanceof OrderStatusChanged);
        if (to == OrderStatus.CANCELLED) {
            assertThat(events).anyMatch(e -> e instanceof OrderCancelled);
        }
        if (to == OrderStatus.DELIVERED) {
            assertThat(events).anyMatch(e -> e instanceof OrderDelivered);
        }

        // Stock side-effect delegation.
        switch (transition.sideEffect()) {
            case COMMIT -> verify(inventoryService, times(1))
                    .commitReservedStock(eq(List.of(new StockDelta(20L, 2))), any());
            case RELEASE -> verify(inventoryService, times(1))
                    .releaseReservedStock(eq(List.of(new StockDelta(20L, 2))), any());
            case RESTOCK -> verify(inventoryService, times(1))
                    .restockCancelledOrder(eq(List.of(new StockDelta(20L, 2))), any());
            case DELIVER, NONE -> {
                // no stock change
            }
        }
    }

    // ------------------------------------------------------------------
    // Focused tests
    // ------------------------------------------------------------------

    @Test
    void vendorRejectRequiresReason() {
        order.setStatus(OrderStatus.PLACED);
        assertThatThrownBy(() -> stateMachine.transition(1L, OrderStatus.REJECTED,
                ActorType.VENDOR, 11L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                .hasMessageContaining("Reason is required");
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void vendorRejectSucceedsWithReason() {
        order.setStatus(OrderStatus.PLACED);
        stateMachine.transition(1L, OrderStatus.REJECTED, ActorType.VENDOR, 11L, "Out of stock");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getPaymentStatus()).isEqualTo(Order.PaymentStatus.PENDING);
        verify(inventoryService).releaseReservedStock(anyList(), any());
    }

    @Test
    void adminCancelRequiresReason() {
        order.setStatus(OrderStatus.PLACED);
        assertThatThrownBy(() -> stateMachine.transition(1L, OrderStatus.CANCELLED,
                ActorType.ADMIN, 12L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void customerCancelDoesNotRequireReason() {
        order.setStatus(OrderStatus.PLACED);
        stateMachine.transition(1L, OrderStatus.CANCELLED, ActorType.CUSTOMER, 10L, null);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryService).releaseReservedStock(anyList(), any());
    }

    @Test
    void terminalStateRejectsOutgoingTransition() {
        order.setStatus(OrderStatus.DELIVERED);
        assertThatThrownBy(() -> stateMachine.transition(1L, OrderStatus.CONFIRMED,
                ActorType.VENDOR, 11L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT)
                .hasMessageContaining("already in terminal state");
        order.setStatus(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> stateMachine.transition(1L, OrderStatus.PLACED,
                ActorType.SYSTEM, null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT);
        order.setStatus(OrderStatus.REJECTED);
        assertThatThrownBy(() -> stateMachine.transition(1L, OrderStatus.PLACED,
                ActorType.SYSTEM, null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT);
    }

    @Test
    void codCollectionRecordedOnDelivery() {
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        stateMachine.transition(1L, OrderStatus.DELIVERED, ActorType.VENDOR, 11L, null);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getPaymentStatus()).isEqualTo(Order.PaymentStatus.COLLECTED);
        ArgumentCaptor<ApplicationEvent> eventCaptor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).anyMatch(e -> e instanceof OrderDelivered);
    }

    @Test
    void onlinePaymentDeliveryDoesNotForceCollected() {
        order.setPaymentMethod(Order.PaymentMethod.ONLINE);
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        stateMachine.transition(1L, OrderStatus.DELIVERED, ActorType.VENDOR, 11L, null);
        assertThat(order.getPaymentStatus()).isEqualTo(Order.PaymentStatus.PENDING);
    }

    @Test
    void orderNotFoundReturns404() {
        when(orderRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> stateMachine.transition(999L, OrderStatus.CONFIRMED,
                ActorType.VENDOR, 11L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    @Test
    void actorUserNotFoundReturns404() {
        order.setStatus(OrderStatus.PLACED);
        when(userRepository.findById(11L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> stateMachine.transition(1L, OrderStatus.CONFIRMED,
                ActorType.VENDOR, 11L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Stream<Arguments> allTransitions() {
        List<Arguments> combinations = new ArrayList<>();
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                for (ActorType actor : ActorType.values()) {
                    combinations.add(Arguments.of(from, to, actor));
                }
            }
        }
        return combinations.stream();
    }

    private Long actorUserId(ActorType actor) {
        return switch (actor) {
            case SYSTEM -> null;
            case CUSTOMER -> 10L;
            case VENDOR -> 11L;
            case ADMIN -> 12L;
        };
    }

    private User actorFor(ActorType actor) {
        return switch (actor) {
            case SYSTEM -> null;
            case CUSTOMER -> customerUser;
            case VENDOR -> vendorUser;
            case ADMIN -> adminUser;
        };
    }

    private String normalize(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }
}
