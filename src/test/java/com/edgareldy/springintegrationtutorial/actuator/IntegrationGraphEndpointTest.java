package com.edgareldy.springintegrationtutorial.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springintegrationtutorial.TestcontainersConfiguration;
import com.edgareldy.springintegrationtutorial.config.IntegrationConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Checks that the dev profile exposes the Actuator integrationgraph endpoint, publicly, with the
 * flow's channels in it.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
// The real dev profile is activated, so the test reads the exposure list from application-dev.yml
// itself. Its localhost datasource is ignored: the @ServiceConnection of the Testcontainers
// configuration takes precedence over spring.datasource.* properties. The test profile comes last, so
// its isolated directories win over the repository folders the dev profile would otherwise use.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({"dev", "test"})
class IntegrationGraphEndpointTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void _01_ShouldDescribeTheIntakeFlow_WhenAnAnonymousCallerRequestsTheGraph() {
        // IntegrationGraphServer walks every channel, endpoint and adapter registered in the context and
        // builds a graph of nodes and links; the endpoint serves it as JSON. errorChannel is declared by
        // Spring Integration itself, the two channels by IntegrationConfig. An annotated component method
        // gives an endpoint named <bean>.<method>.<annotation>, an annotated @Bean method <bean>.<annotation>.
        assertThat(mvc.get().uri("/actuator/integrationgraph"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> json.assertThat().extractingPath("$.nodes[*].name").asArray()
                        .contains(IntegrationConfig.INTAKE_CHANNEL, IntegrationConfig.ORDER_COMMAND_CHANNEL,
                                "errorChannel",
                                "incomingOrderLines.inboundChannelAdapter",
                                "rawOrderTransformer.transform.transformer",
                                "orderPersistenceActivator.persist.serviceActivator"));
    }
}
