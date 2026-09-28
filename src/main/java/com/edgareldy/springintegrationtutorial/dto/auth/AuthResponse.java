package com.edgareldy.springintegrationtutorial.dto.auth;

/**
 * Payload returned by a successful login: the token to send back as {@code Authorization: Bearer <token>}.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 *
 * @param accessToken the signed JWT
 * @param tokenType   always {@code Bearer}
 * @param expiresIn   the token lifetime, in seconds
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {

    /**
     * @param accessToken the signed JWT
     * @param expiresIn   the token lifetime, in seconds
     * @return a bearer token response
     */
    public static AuthResponse bearer(String accessToken, long expiresIn) {
        return new AuthResponse(accessToken, "Bearer", expiresIn);
    }
}
