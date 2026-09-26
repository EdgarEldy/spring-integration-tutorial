package com.edgareldy.springintegrationtutorial.service;

import com.edgareldy.springintegrationtutorial.dto.auth.AuthResponse;
import com.edgareldy.springintegrationtutorial.dto.auth.LoginRequest;
import com.edgareldy.springintegrationtutorial.dto.auth.RegisterRequest;
import com.edgareldy.springintegrationtutorial.dto.auth.UserResponse;

/**
 * Account operations of the auth REST layer: registration, login, current profile, and the creation
 * of the ADMIN account at startup. Emails are compared case-insensitively (stored lower-case).
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
public interface UserService {

    /**
     * Creates an enabled account with the {@code USER} role.
     *
     * @param request the new account
     * @return the created account
     * @throws com.edgareldy.springintegrationtutorial.exception.BusinessRuleException if the email is taken
     */
    UserResponse register(RegisterRequest request);

    /**
     * Checks the credentials and issues a JWT.
     *
     * @param request the credentials
     * @return the bearer token
     * @throws org.springframework.security.core.AuthenticationException if the credentials are wrong or
     *                                                                   the account is disabled or locked
     */
    AuthResponse login(LoginRequest request);

    /**
     * @param email the authenticated caller's email
     * @return the caller's profile
     * @throws com.edgareldy.springintegrationtutorial.exception.ResourceNotFoundException if the account
     *                                                                                     no longer exists
     */
    UserResponse getCurrentUser(String email);

    /**
     * Creates an enabled account with the {@code ADMIN} role, unless an account already uses the email.
     *
     * @param email    the admin email
     * @param password the clear admin password
     * @return {@code true} if the account was created, {@code false} if it already existed
     */
    boolean createAdminIfAbsent(String email, String password);
}
