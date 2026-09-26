package com.edgareldy.springintegrationtutorial.config;

import com.edgareldy.springintegrationtutorial.entity.Role;
import com.edgareldy.springintegrationtutorial.repository.UserRepository;
import com.edgareldy.springintegrationtutorial.security.JwtAuthFilter;
import com.edgareldy.springintegrationtutorial.security.JwtService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * HTTP security of the REST layer: stateless JWT authentication, public health, documentation,
 * integration graph and auth endpoints, authentication on every order route and the {@code ADMIN} role
 * on the order review endpoints. The file-drop channel has no HTTP surface and is not subject to these
 * rules: it is a trusted, internal-only input by design.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@Configuration
// Enables @PreAuthorize("hasRole('ADMIN')") on controller or service methods, for rules that are
// easier to read next to the code they protect than in the URL patterns below.
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * @param http              the security builder
     * @param jwtService        verifies the bearer tokens
     * @param userDetailsService reloads the account named by a token
     * @param exceptionResolver Spring MVC's exception resolver, which reaches {@code GlobalExceptionHandler}
     * @return the filter chain applied to every request
     * @throws Exception if the chain cannot be built
     */
    // Declaring a SecurityFilterChain bean replaces Spring Boot's default one (HTTP Basic and a
    // generated password on every URL). Health probes must stay reachable without credentials, and a
    // REST API authenticated by tokens needs neither server-side sessions nor CSRF protection, which
    // only defends cookie-based sessions.
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                            UserDetailsService userDetailsService,
                                            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // The rules are evaluated in order, first match wins: the specific admin-only routes
                // come before the broader /api/v1/orders/** pattern.
                .authorizeHttpRequests(auth -> auth
                        // /actuator/integrationgraph is only exposed in the dev profile: anywhere else the
                        // request passes this rule and gets a 404, so opening it costs nothing outside dev.
                        .requestMatchers("/actuator/health/**", "/actuator/integrationgraph",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login")
                        .permitAll()
                        // The review decision endpoints (feature/message-routing) resolve a pending order:
                        // only an administrator may approve or reject one.
                        .requestMatchers("/api/v1/orders/*/approve", "/api/v1/orders/*/reject")
                        .hasRole(Role.ADMIN)
                        // Both the intake gateway and the review endpoints require a caller.
                        .requestMatchers("/api/v1/orders/**").authenticated()
                        .anyRequest().authenticated())
                // The JWT filter runs before the form-login filter position, so the request is already
                // authenticated when the authorization rules above are evaluated.
                .addFilterBefore(new JwtAuthFilter(jwtService, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class)
                // Security failures happen in the filter chain, before Spring MVC dispatches the request,
                // so @RestControllerAdvice never sees them. Handing them to MVC's exception resolver makes
                // GlobalExceptionHandler render them: 401 for an anonymous caller ("who are you?"), 403
                // for an authenticated caller without the role ("you may not"), both as ApiResponse.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) ->
                                exceptionResolver.resolveException(request, response, null, ex))
                        .accessDeniedHandler((request, response, ex) ->
                                exceptionResolver.resolveException(request, response, null, ex)))
                .build();
    }

    /**
     * @param userRepository the account store
     * @return the lookup used by the login and by the JWT filter, keyed by the (lower-case) email
     */
    // Spring Security never reads the entity directly: it works with UserDetails. Mapping the account
    // here keeps the User entity free of any security interface.
    @Bean
    UserDetailsService userDetailsService(UserRepository userRepository) {
        return email -> userRepository.findByEmail(email)
                .map(user -> User.withUsername(user.getEmail())
                        .password(user.getPassword())
                        .disabled(!user.isEnabled())
                        .accountLocked(user.isAccountLocked())
                        // roles("ADMIN") becomes the authority ROLE_ADMIN, which hasRole("ADMIN") checks.
                        .roles(user.getRoles().stream().map(Role::getRoleName).toArray(String[]::new))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
    }

    /**
     * @return the delegating encoder, BCrypt by default
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * @param userDetailsService the account lookup
     * @param passwordEncoder    the hash comparison
     * @return the manager the login uses to check a password
     */
    // The DaoAuthenticationProvider is the standard username/password check: load the account, check
    // it is enabled and not locked, compare the password with the stored hash. Exposing the manager as
    // a bean lets the login service call it instead of reimplementing those checks.
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
