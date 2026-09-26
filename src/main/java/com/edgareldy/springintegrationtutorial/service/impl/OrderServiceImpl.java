package com.edgareldy.springintegrationtutorial.service.impl;

import com.edgareldy.springintegrationtutorial.entity.Customer;
import com.edgareldy.springintegrationtutorial.entity.Order;
import com.edgareldy.springintegrationtutorial.entity.OrderStatus;
import com.edgareldy.springintegrationtutorial.entity.Product;
import com.edgareldy.springintegrationtutorial.exception.ResourceNotFoundException;
import com.edgareldy.springintegrationtutorial.integration.message.OrderCommand;
import com.edgareldy.springintegrationtutorial.repository.CustomerRepository;
import com.edgareldy.springintegrationtutorial.repository.OrderRepository;
import com.edgareldy.springintegrationtutorial.repository.ProductRepository;
import com.edgareldy.springintegrationtutorial.service.OrderService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link OrderService}: the only place where an order row is created.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    // @Transactional wraps the lookups and the insert in one database transaction: the price read for
    // the snapshot and the saved order belong to the same unit of work, and a failure leaves no row.
    @Override
    @Transactional
    public Order receive(OrderCommand command) {
        Customer customer = customerRepository.findById(command.customerId())
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", command.customerId()));
        Product product = productRepository.findById(command.productId())
                .orElseThrow(() -> ResourceNotFoundException.of("Product", command.productId()));

        Order order = new Order();
        order.setCustomer(customer);
        order.setProduct(product);
        order.setQuantity(command.quantity());
        // Snapshot taken now and never recalculated, rounded to the two decimals of the total column.
        order.setTotal(product.getUnitPrice()
                .multiply(BigDecimal.valueOf(command.quantity()))
                .setScale(2, RoundingMode.HALF_UP));
        order.setSource(command.source());
        order.setStatus(OrderStatus.RECEIVED);
        return orderRepository.save(order);
    }
}
