package com.edgareldy.springintegrationtutorial.support;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Inserts the categories, products and customers a test needs and reads back the orders the flow
 * created. Every name and email is unique, so tests sharing one database (the cached Spring context)
 * never collide and each one only looks at its own rows.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
public final class CommerceTestData {

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc access to the test database
     */
    public CommerceTestData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts a product, in a category of its own.
     *
     * @param unitPrice the product's unit price, e.g. "19.99"
     * @return the product id
     */
    public long product(String unitPrice) {
        Long categoryId = jdbc.queryForObject(
                "INSERT INTO categories (category_name) VALUES (?) RETURNING id", Long.class, "Category " + unique());
        return jdbc.queryForObject(
                "INSERT INTO products (category_id, product_name, unit_price) VALUES (?, ?, ?) RETURNING id",
                Long.class, categoryId, "Product " + unique(), new BigDecimal(unitPrice));
    }

    /**
     * @return the id of a new customer with a unique email
     */
    public long customer() {
        return jdbc.queryForObject(
                "INSERT INTO customers (first_name, last_name, email) VALUES ('Test', 'Customer', ?) RETURNING id",
                Long.class, unique() + "@example.com");
    }

    /**
     * @param customerId the customer
     * @return how many orders the customer has
     */
    public int orderCount(long customerId) {
        return jdbc.queryForObject("SELECT count(*) FROM orders WHERE customer_id = ?", Integer.class, customerId);
    }

    /**
     * @param customerId a customer with exactly one order
     * @return that order's row (product_id, quantity, total, source, status)
     */
    public Map<String, Object> singleOrderOf(long customerId) {
        return jdbc.queryForMap(
                "SELECT product_id, quantity, total, source, status FROM orders WHERE customer_id = ?", customerId);
    }

    private static String unique() {
        return UUID.randomUUID().toString();
    }
}
