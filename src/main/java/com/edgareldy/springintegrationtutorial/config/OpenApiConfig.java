package com.edgareldy.springintegrationtutorial.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Describes the HTTP API in the OpenAPI document served at /v3/api-docs and rendered by Swagger UI.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    /**
     * @return the OpenAPI description of the API
     */
    // springdoc builds the document from the controllers on its own; this bean only adds what it cannot
    // infer: the API's title, and the fact that calls carry a JWT as a bearer token. Declaring the scheme
    // and requiring it globally gives Swagger UI its "Authorize" button, so a token obtained from the
    // login endpoint is sent with every request tried from the page.
    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Spring Integration tutorial API")
                        .description("Order intake over HTTP, converging with a file-drop channel onto one integration flow")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
