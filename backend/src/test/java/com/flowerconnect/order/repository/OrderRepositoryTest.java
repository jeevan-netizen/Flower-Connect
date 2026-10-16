package com.flowerconnect.order.repository;

import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderRepositoryTest {

    @Mock
    private OrderRepository orderRepository;

    @Test
    void testCountBookedOrdersForSlotOnlyCountsPlacedAndConfirmed() {
        // Arrange
        Long vendorId = 1L;
        LocalDateTime slotStart = LocalDateTime.of(2026, 10, 18, 10, 0);
        
        // Mock the repository to return count based on our criteria
        when(orderRepository.countBookedOrdersForSlot(vendorId, slotStart))
                .thenReturn(2L); // Only PLACED and CONFIRMED orders
        
        // Act
        long count = orderRepository.countBookedOrdersForSlot(vendorId, slotStart);
        
        // Assert
        assertEquals(2, count);
        
        // Verify the method was called with correct parameters
        verify(orderRepository).countBookedOrdersForSlot(vendorId, slotStart);
    }

    @Test
    void testCountBookedOrdersForSlotReturnsZeroWhenNoMatchingOrders() {
        // Arrange
        Long vendorId = 1L;
        LocalDateTime slotStart = LocalDateTime.of(2026, 10, 18, 10, 0);
        
        // Mock the repository to return zero count
        when(orderRepository.countBookedOrdersForSlot(vendorId, slotStart))
                .thenReturn(0L);
        
        // Act
        long count = orderRepository.countBookedOrdersForSlot(vendorId, slotStart);
        
        // Assert
        assertEquals(0, count);
    }
}