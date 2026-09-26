package com.edgareldy.springintegrationtutorial.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/auth/register}: the identity and the chosen password of a new account.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 *
 * @param firstName the first name
 * @param lastName  the last name
 * @param email     the email, used as the login identifier
 * @param password  the clear password, hashed before storage
 */
public record RegisterRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        // BCrypt only hashes the first 72 bytes of a password: a longer one would be silently truncated.
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
