package com.edgareldy.springintegrationtutorial.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springintegrationtutorial.TestcontainersConfiguration;
import com.edgareldy.springintegrationtutorial.entity.Category;
import com.edgareldy.springintegrationtutorial.entity.Customer;
import com.edgareldy.springintegrationtutorial.entity.Order;
import com.edgareldy.springintegrationtutorial.entity.OrderSource;
import com.edgareldy.springintegrationtutorial.entity.OrderStatus;
import com.edgareldy.springintegrationtutorial.entity.Product;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Checks the commerce entities and their repositories against the real schema created by Flyway.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
// @DataJpaTest starts only the JPA slice (entities, repositories, Flyway, a datasource) and wraps each
// test in a rolled back transaction. replace = NONE keeps the PostgreSQL container of
// TestcontainersConfiguration instead of the embedded database the slice would otherwise substitute,
// so the CHECK constraints and column types are those of production.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OrderRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void _01_ShouldStoreTheEnumNamesAcceptedByTheCheckConstraints_WhenAnOrderIsSaved() {
        Order order = orderRepository.saveAndFlush(newOrder(OrderSource.FILE, OrderStatus.RECEIVED));

        Map<String, Object> row = jdbc.queryForMap("SELECT source, status, total FROM orders WHERE id = ?", order.getId());

        assertThat(row).containsEntry("source", "FILE").containsEntry("status", "RECEIVED");
        assertThat((BigDecimal) row.get("total")).isEqualByComparingTo("59.97");
    }

    @Test
    void _02_ShouldReloadTheOrderWithItsCustomerAndProduct_WhenItIsReadBack() {
        Order saved = orderRepository.saveAndFlush(newOrder(OrderSource.API, OrderStatus.PENDING_REVIEW));
        // Detaches everything, so the read below really goes back to the database.
        entityManager.clear();

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getSource()).isEqualTo(OrderSource.API);
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PENDING_REVIEW);
        assertThat(reloaded.getQuantity()).isEqualTo(3);
        assertThat(reloaded.getCustomer().getEmail()).isEqualTo("grace@example.com");
        assertThat(reloaded.getProduct().getUnitPrice()).isEqualByComparingTo("19.99");
        assertThat(reloaded.getProduct().getCategory().getCategoryName()).isEqualTo("Repository test");
    }

    private Order newOrder(OrderSource source, OrderStatus status) {
        Category category = new Category();
        category.setCategoryName("Repository test");
        categoryRepository.save(category);

        Product product = new Product();
        product.setCategory(category);
        product.setProductName("Cable");
        product.setUnitPrice(new BigDecimal("19.99"));
        productRepository.save(product);

        Customer customer = new Customer();
        customer.setFirstName("Grace");
        customer.setLastName("Hopper");
        customer.setEmail("grace@example.com");
        customerRepository.save(customer);

        Order order = new Order();
        order.setCustomer(customer);
        order.setProduct(product);
        order.setQuantity(3);
        order.setTotal(new BigDecimal("59.97"));
        order.setSource(source);
        order.setStatus(status);
        return order;
    }
}
