package com.edgareldy.springintegrationtutorial.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/v1/auth/login}: the credentials exchanged for a JWT.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 *
 * @param email    the account email
 * @param password the clear password
 */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {
}
