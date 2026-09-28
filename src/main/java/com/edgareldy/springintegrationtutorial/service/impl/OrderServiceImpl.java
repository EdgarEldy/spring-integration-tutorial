package com.edgareldy.springintegrationtutorial.service.impl;

import com.edgareldy.springintegrationtutorial.entity.Customer;
import com.edgareldy.springintegrationtutorial.entity.Order;
import com.edgareldy.springintegrationtutorial.entity.OrderStatus;
import com.edgareldy.springintegrationtutorial.entity.Product;
import com.edgareldy.springintegrationtutorial.exception.BusinessRuleException;
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

    @Override
    @Transactional
    public Order updateStatus(Long orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        // A managed entity: the change is written by dirty checking when the transaction commits.
        order.setStatus(status);
        return order;
    }

    @Override
    @Transactional
    public Order resolveReview(Long orderId, OrderStatus outcome) {
        if (outcome != OrderStatus.APPROVED && outcome != OrderStatus.REJECTED) {
            throw new IllegalArgumentException("A review ends with APPROVED or REJECTED, not " + outcome);
        }
        if (orderRepository.updateStatusIfCurrent(orderId, OrderStatus.PENDING_REVIEW, outcome) == 0) {
            // Nothing updated: either the order does not exist, or it is not (or no longer) pending review.
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
            throw new BusinessRuleException("Order with id " + orderId + " is not pending review (status "
                    + order.getStatus() + ")");
        }
        return orderRepository.findById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
    }
}
