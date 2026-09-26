package com.edgareldy.springintegrationtutorial.controller;

import com.edgareldy.springintegrationtutorial.dto.ApiResponse;
import com.edgareldy.springintegrationtutorial.dto.auth.AuthResponse;
import com.edgareldy.springintegrationtutorial.dto.auth.LoginRequest;
import com.edgareldy.springintegrationtutorial.dto.auth.RegisterRequest;
import com.edgareldy.springintegrationtutorial.dto.auth.UserResponse;
import com.edgareldy.springintegrationtutorial.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conventional REST endpoints of the auth feature: register, log in (returns a JWT) and read the
 * current profile. Authentication is not an integration flow: these calls go straight to the service.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * @param request the new account
     * @return 201 with the created account
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(userService.register(request), "Account registered");
    }

    /**
     * @param request the credentials
     * @return 200 with the bearer token
     */
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(userService.login(request), "Login successful");
    }

    /**
     * @param principal the account authenticated by the JWT filter
     * @return 200 with the caller's profile
     */
    // @AuthenticationPrincipal injects the principal the JWT filter put in the security context, so the
    // controller never parses the token itself.
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal UserDetails principal) {
        return ApiResponse.success(userService.getCurrentUser(principal.getUsername()), "Current user");
    }
}
