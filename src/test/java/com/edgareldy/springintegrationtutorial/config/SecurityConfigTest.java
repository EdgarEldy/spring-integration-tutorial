package com.edgareldy.springintegrationtutorial.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springintegrationtutorial.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Checks the baseline security filter chain: public health, documentation and integration graph,
 * 401 for anonymous callers everywhere else.
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
class SecurityConfigTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void _01_ShouldAnswer401_WhenAnAnonymousCallerRequestsAnOrderRoute() {
        assertThat(mvc.post().uri("/api/v1/orders/intake")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void _02_ShouldServeTheOpenApiDocumentWithTheBearerScheme_WhenCalledWithoutCredentials() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.components.securitySchemes.bearerAuth.scheme").isEqualTo("bearer");
    }

    @Test
    void _03_ShouldAnswer404Not401_WhenTheIntegrationGraphIsRequestedOutsideTheDevProfile() {
        // The route is public, but the endpoint is not exposed outside dev: the caller learns it does
        // not exist, not that credentials are missing.
        assertThat(mvc.get().uri("/actuator/integrationgraph")).hasStatus(HttpStatus.NOT_FOUND);
    }
}
