package com.edgareldy.springintegrationtutorial.service.impl;

import com.edgareldy.springintegrationtutorial.dto.auth.AuthResponse;
import com.edgareldy.springintegrationtutorial.dto.auth.LoginRequest;
import com.edgareldy.springintegrationtutorial.dto.auth.RegisterRequest;
import com.edgareldy.springintegrationtutorial.dto.auth.UserResponse;
import com.edgareldy.springintegrationtutorial.entity.Role;
import com.edgareldy.springintegrationtutorial.entity.User;
import com.edgareldy.springintegrationtutorial.exception.BusinessRuleException;
import com.edgareldy.springintegrationtutorial.exception.ResourceNotFoundException;
import com.edgareldy.springintegrationtutorial.repository.RoleRepository;
import com.edgareldy.springintegrationtutorial.repository.UserRepository;
import com.edgareldy.springintegrationtutorial.security.JwtService;
import com.edgareldy.springintegrationtutorial.service.UserService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link UserService}: stores accounts with a hashed password, delegates the credential
 * check to Spring Security's {@link AuthenticationManager} and issues the token with {@link JwtService}.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    static final String EMAIL_TAKEN = "Email is already registered";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException(EMAIL_TAKEN);
        }
        User user = newUser(request.firstName(), request.lastName(), email, request.password(), Role.USER);
        try {
            // Flushing now surfaces the unique constraint inside this method: two registrations racing
            // past the existsByEmail check still end with a 422 instead of an unexpected 500.
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException(EMAIL_TAKEN);
        }
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // The AuthenticationManager loads the account through the UserDetailsService, compares the
        // password hash and checks the enabled and locked flags: an unknown email and a wrong password
        // both end in the same BadCredentialsException, so the response never reveals which emails exist.
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(normalize(request.email()), request.password()));
        UserDetails principal = (UserDetails) authentication.getPrincipal();
        return AuthResponse.bearer(jwtService.generateToken(principal), jwtService.getExpiration().toSeconds());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        return userRepository.findByEmail(normalize(email))
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User " + email + " not found"));
    }

    @Override
    @Transactional
    public boolean createAdminIfAbsent(String email, String password) {
        String normalized = normalize(email);
        if (userRepository.existsByEmail(normalized)) {
            return false;
        }
        userRepository.save(newUser("Admin", "Account", normalized, password, Role.ADMIN));
        return true;
    }

    private User newUser(String firstName, String lastName, String email, String password, String roleName) {
        User user = new User();
        user.setFirstName(firstName.trim());
        user.setLastName(lastName.trim());
        user.setEmail(email);
        // Only the hash is stored. The delegating encoder prefixes it with its algorithm ({bcrypt}...),
        // so the algorithm can change later without invalidating the existing hashes.
        user.setPassword(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.getRoles().add(roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role " + roleName + " is not seeded")));
        return user;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
