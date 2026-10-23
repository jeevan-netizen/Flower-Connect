package com.flowerconnect.payment.provider;

import com.flowerconnect.order.domain.Order;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.domain.Payment.PaymentMethod;
import com.flowerconnect.payment.domain.Payment.PaymentStatus;
import com.flowerconnect.payment.gateway.PaymentGateway;
import com.flowerconnect.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Cash-on-delivery payment provider (plan Phase 7, task 7.2).
 *
 * <p>COD involves no external provider and no network call. Creating a COD
 * payment simply persists a {@code PENDING} payment record; the money is
 * collected later at delivery, at which point
 * {@link com.flowerconnect.payment.service.PaymentService#collectPayment}
 * moves it to {@code COLLECTED}.
 *
 * <p>Registered as a Spring bean so {@link
 * com.flowerconnect.payment.service.PaymentService} can resolve the COD
 * provider without an online alternative present in Stage 1.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodPaymentProvider implements PaymentGateway {

    private static final String PROVIDER = "cod";

    private final PaymentRepository paymentRepository;

    @Override
    public Payment createPayment(Order order, BigDecimal amount) {
        // COD makes no external call: persist a pending record only.
        Payment payment = Payment.builder()
                .order(order)
                .method(PaymentMethod.COD)
                .provider(PROVIDER)
                .providerReference(null)
                .amount(amount)
                .status(PaymentStatus.PENDING)
                .collectedAt(null)
                .build();
        Payment saved = paymentRepository.saveAndFlush(payment);
        log.info("Created COD payment {} for order {} amount {}", saved.getId(), order.getId(), amount);
        return saved;
    }
}
