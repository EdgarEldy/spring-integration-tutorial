package com.edgareldy.springintegrationtutorial;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test configuration starting a real PostgreSQL 16 container for every test that needs a database.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * @return the PostgreSQL container, shared by every context that imports this configuration
     */
    // @ServiceConnection makes Spring Boot derive the datasource URL, user and password from the
    // running container, so no spring.datasource.* property is needed in tests. Flyway then applies
    // V1__init_schema.sql to it exactly as in production, which is the point of testing against the
    // same PostgreSQL version instead of an in-memory database.
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16"));
    }
}
