package com.edgareldy.springintegrationtutorial.flyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edgareldy.springintegrationtutorial.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks what the V1 migration leaves in a real PostgreSQL database.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
// Each test runs in a transaction rolled back at the end, so the rows inserted to probe the
// constraints never leak into the database shared by the other context tests.
@Transactional
class FlywaySchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void _01_ShouldHaveAppliedV1Successfully_WhenTheApplicationStarts() {
        Boolean success = jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);

        assertThat(success).isTrue();
    }

    @Test
    void _02_ShouldCreateTheTablesOfBothDomains_WhenV1IsApplied() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tables).contains(
                "users", "roles", "role_user", "categories", "products", "customers", "orders");
    }

    @Test
    void _03_ShouldSeedTheAdminAndUserRolesOnly_WhenV1IsApplied() {
        List<String> roles = jdbc.queryForList("SELECT role_name FROM roles", String.class);
        Integer users = jdbc.queryForObject("SELECT count(*) FROM users", Integer.class);

        assertThat(roles).containsExactlyInAnyOrder("ADMIN", "USER");
        assertThat(users).isZero();
    }

    @Test
    void _04_ShouldDeclareNoForeignKeyBetweenTheTwoDomains_WhenV1IsApplied() {
        // Foreign keys only link tables of the same domain: the commerce tables never reference the
        // identity tables, and the other way round.
        Integer crossDomainForeignKeys = jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.referential_constraints rc
                JOIN information_schema.table_constraints child ON child.constraint_name = rc.constraint_name
                JOIN information_schema.table_constraints parent ON parent.constraint_name = rc.unique_constraint_name
                WHERE (child.table_name IN ('users', 'roles', 'role_user'))
                   <> (parent.table_name IN ('users', 'roles', 'role_user'))
                """, Integer.class);
        // role_user -> users, role_user -> roles, products -> categories, orders -> customers, orders -> products
        Integer allForeignKeys = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.referential_constraints", Integer.class);

        assertThat(crossDomainForeignKeys).isZero();
        assertThat(allForeignKeys).isEqualTo(5);
    }

    @Test
    void _05_ShouldRejectTheOrder_WhenItsSourceIsNotApiOrFile() {
        long[] ids = insertCustomerAndProduct();

        assertThatThrownBy(() -> insertOrder(ids, 1, "EMAIL", "RECEIVED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void _06_ShouldRejectTheOrder_WhenItsStatusIsUnknown() {
        long[] ids = insertCustomerAndProduct();

        assertThatThrownBy(() -> insertOrder(ids, 1, "API", "SHIPPED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void _07_ShouldRejectTheOrder_WhenItsQuantityIsNotPositive() {
        long[] ids = insertCustomerAndProduct();

        assertThatThrownBy(() -> insertOrder(ids, 0, "FILE", "RECEIVED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void _08_ShouldAcceptTheOrder_WhenEveryColumnIsValid() {
        long[] ids = insertCustomerAndProduct();

        insertOrder(ids, 3, "FILE", "RECEIVED");

        assertThat(jdbc.queryForObject("SELECT total FROM orders WHERE customer_id = ?", String.class, ids[0]))
                .isEqualTo("30.00");
    }

    private long[] insertCustomerAndProduct() {
        Long categoryId = jdbc.queryForObject(
                "INSERT INTO categories (category_name) VALUES ('Schema test') RETURNING id", Long.class);
        Long productId = jdbc.queryForObject(
                "INSERT INTO products (category_id, product_name, unit_price) VALUES (?, 'Widget', 10.00) RETURNING id",
                Long.class, categoryId);
        Long customerId = jdbc.queryForObject(
                "INSERT INTO customers (first_name, last_name, email) VALUES ('Ada', 'Lovelace', 'ada@example.com') RETURNING id",
                Long.class);
        return new long[] {customerId, productId};
    }

    private void insertOrder(long[] customerAndProduct, int quantity, String source, String status) {
        jdbc.update("""
                INSERT INTO orders (customer_id, product_id, quantity, total, source, status)
                VALUES (?, ?, ?, ? * 10.00, ?, ?)
                """, customerAndProduct[0], customerAndProduct[1], quantity, quantity, source, status);
    }
}
