package com.edgareldy.springintegrationtutorial.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springintegrationtutorial.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checks that {@link GlobalExceptionHandler} turns each kind of failure into the right status and
 * an {@link ApiResponse} error body.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
class GlobalExceptionHandlerTest {

    // A standalone MockMvc only registers the given controller and advice: no Spring context, no
    // security, no database. It isolates the handler's mapping from everything else.
    private final MockMvcTester mvc = MockMvcTester.create(MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build());

    @Test
    void _01_ShouldReturn404WithErrorEnvelope_WhenAResourceIsNotFound() {
        assertThat(mvc.get().uri("/test/not-found"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.success").isEqualTo(false);
                    json.assertThat().extractingPath("$.message").isEqualTo("Customer with id 42 not found");
                    json.assertThat().extractingPath("$.data").isNull();
                    json.assertThat().extractingPath("$.timestamp").isNotNull();
                });
    }

    @Test
    void _02_ShouldReturn422_WhenABusinessRuleIsViolated() {
        assertThat(mvc.get().uri("/test/business-rule"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.message").isEqualTo("Order is not pending review");
    }

    @Test
    void _03_ShouldReturn400WithFieldErrors_WhenTheBodyFailsValidation() {
        assertThat(mvc.post().uri("/test/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.success").isEqualTo(false);
                    json.assertThat().extractingPath("$.message").isEqualTo("Validation failed");
                    json.assertThat().extractingPath("$.data.name").isEqualTo("must not be blank");
                });
    }

    @Test
    void _04_ShouldReturn400_WhenTheBodyIsNotValidJson() {
        assertThat(mvc.post().uri("/test/validated").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.message").isEqualTo("Malformed request body");
    }

    @Test
    void _05_ShouldReturn403_WhenAccessIsDenied() {
        assertThat(mvc.get().uri("/test/forbidden"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.message").isEqualTo("Access denied");
    }

    @Test
    void _06_ShouldKeepSpringMvcStatusAndHeaders_WhenTheHttpMethodIsNotSupported() {
        assertThat(mvc.delete().uri("/test/forbidden"))
                .hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
                .hasHeader(HttpHeaders.ALLOW, "GET")
                .bodyJson().extractingPath("$.success").isEqualTo(false);
    }

    @Test
    void _07_ShouldReturn500WithoutInternals_WhenAnUnexpectedExceptionOccurs() {
        assertThat(mvc.get().uri("/test/unexpected"))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson().extractingPath("$.message").isEqualTo("Unexpected error");
    }

    @Test
    void _08_ShouldReportTheObjectLevelRule_WhenAClassLevelConstraintFails() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "orderIntakeRequest");
        bindingResult.reject("ConsistentOrder", "order is inconsistent");
        MethodParameter parameter = new MethodParameter(
                ThrowingController.class.getDeclaredMethod("validated", NamedRequest.class), 0);

        ResponseEntity<ApiResponse<Map<String, String>>> response = new GlobalExceptionHandler()
                .handleValidation(new MethodArgumentNotValidException(parameter, bindingResult));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().data()).containsEntry("orderIntakeRequest", "order is inconsistent");
    }

    /**
     * Request body with one constrained field.
     * <p>
     * Created edgar.muhamyangabo on 9/26/26
     * Author : edgar.muhamyangabo
     * Date : 9/26/26
     * Project : spring-integration-tutorial
     *
     * @param name a mandatory name
     */
    record NamedRequest(@NotBlank String name) {
    }

    /**
     * Test-only controller throwing one exception per endpoint.
     * <p>
     * Created edgar.muhamyangabo on 9/26/26
     * Author : edgar.muhamyangabo
     * Date : 9/26/26
     * Project : spring-integration-tutorial
     */
    @RestController
    static class ThrowingController {

        @GetMapping("/test/not-found")
        void notFound() {
            throw ResourceNotFoundException.of("Customer", 42);
        }

        @GetMapping("/test/business-rule")
        void businessRule() {
            throw new BusinessRuleException("Order is not pending review");
        }

        @PostMapping("/test/validated")
        void validated(@Valid @RequestBody NamedRequest request) {
        }

        @GetMapping("/test/forbidden")
        void forbidden() {
            throw new AccessDeniedException("nope");
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("secret internal detail");
        }
    }
}
