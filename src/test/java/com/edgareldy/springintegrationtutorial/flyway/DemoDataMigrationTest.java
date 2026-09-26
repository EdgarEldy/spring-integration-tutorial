package com.edgareldy.springintegrationtutorial.flyway;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springintegrationtutorial.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

/**
 * Checks the demo data migration applied by the dev profile: what it inserts and that running it again
 * inserts nothing more.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
// Same annotations and profiles as IntegrationGraphEndpointTest, so both share one cached dev context.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({"dev", "test"})
class DemoDataMigrationTest {

    private static final String DEMO_DATA = "db/dev-data/R__demo_data.sql";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Test
    void _01_ShouldApplyTheDemoDataMigration_WhenTheDevProfileIsActive() {
        Boolean success = jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE script = 'R__demo_data.sql'", Boolean.class);

        assertThat(success).isTrue();
    }

    @Test
    void _02_ShouldOfferProductsOnBothSidesOfTheReviewThreshold_WhenTheDemoDataIsLoaded() {
        List<BigDecimal> prices = jdbc.queryForList("SELECT unit_price FROM products", BigDecimal.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM categories", Integer.class)).isGreaterThanOrEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers", Integer.class)).isGreaterThanOrEqualTo(3);
        assertThat(prices).anyMatch(price -> price.compareTo(new BigDecimal("1000.00")) < 0)
                .anyMatch(price -> price.compareTo(new BigDecimal("1000.00")) >= 0);
    }

    @Test
    void _03_ShouldInsertNothingMore_WhenTheDemoScriptRunsAgain() {
        int before = demoRowCount();

        new ResourceDatabasePopulator(new ClassPathResource(DEMO_DATA)).execute(dataSource);

        assertThat(demoRowCount()).isEqualTo(before);
    }

    private int demoRowCount() {
        return jdbc.queryForObject(
                "SELECT (SELECT count(*) FROM categories) + (SELECT count(*) FROM products) + (SELECT count(*) FROM customers)",
                Integer.class);
    }
}
