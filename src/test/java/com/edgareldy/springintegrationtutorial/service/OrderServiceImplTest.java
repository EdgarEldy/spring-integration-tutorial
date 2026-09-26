package com.edgareldy.springintegrationtutorial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.edgareldy.springintegrationtutorial.entity.Customer;
import com.edgareldy.springintegrationtutorial.entity.Order;
import com.edgareldy.springintegrationtutorial.entity.OrderSource;
import com.edgareldy.springintegrationtutorial.entity.OrderStatus;
import com.edgareldy.springintegrationtutorial.entity.Product;
import com.edgareldy.springintegrationtutorial.exception.ResourceNotFoundException;
import com.edgareldy.springintegrationtutorial.integration.message.OrderCommand;
import com.edgareldy.springintegrationtutorial.repository.CustomerRepository;
import com.edgareldy.springintegrationtutorial.repository.OrderRepository;
import com.edgareldy.springintegrationtutorial.repository.ProductRepository;
import com.edgareldy.springintegrationtutorial.service.impl.OrderServiceImpl;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests of {@link OrderServiceImpl} with mocked repositories: existence checks, total snapshot,
 * initial status and source.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void _01_ShouldSaveAReceivedOrderWithItsSource_WhenCustomerAndProductExist() {
        Customer customer = new Customer();
        Product product = product("25.00");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = orderService.receive(new OrderCommand(1L, 2L, 4, OrderSource.FILE));

        assertThat(order.getCustomer()).isSameAs(customer);
        assertThat(order.getProduct()).isSameAs(product);
        assertThat(order.getQuantity()).isEqualTo(4);
        assertThat(order.getTotal()).isEqualByComparingTo("100.00");
        assertThat(order.getSource()).isEqualTo(OrderSource.FILE);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RECEIVED);
        verify(orderRepository).save(order);
    }

    @Test
    void _02_ShouldSnapshotTheTotalWithTwoDecimals_WhenThePriceHasCents() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(new Customer()));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product("19.99")));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = orderService.receive(new OrderCommand(1L, 2L, 3, OrderSource.API));

        assertThat(order.getTotal()).isEqualTo(new BigDecimal("59.97"));
    }

    @Test
    void _03_ShouldThrowNotFoundWithoutSaving_WhenTheCustomerDoesNotExist() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.receive(new OrderCommand(99L, 2L, 1, OrderSource.API)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer with id 99 not found");
        verifyNoInteractions(productRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void _04_ShouldThrowNotFoundWithoutSaving_WhenTheProductDoesNotExist() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(new Customer()));
        when(productRepository.findById(98L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.receive(new OrderCommand(1L, 98L, 1, OrderSource.FILE)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product with id 98 not found");
        verify(orderRepository, never()).save(any());
    }

    private static Product product(String unitPrice) {
        Product product = new Product();
        product.setUnitPrice(new BigDecimal(unitPrice));
        return product;
    }
}
