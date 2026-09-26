package com.edgareldy.springintegrationtutorial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import com.edgareldy.springintegrationtutorial.service.impl.UserServiceImpl;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests of {@link UserServiceImpl} with mocked repositories, encoder, authentication manager and
 * token service: registration, login, current profile and admin creation.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private UserServiceImpl userService;

    private Role userRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        userRole = new Role(Role.USER);
        adminRole = new Role(Role.ADMIN);
    }

    @Test
    void _01_ShouldCreateAnEnabledUserWithAHashedPassword_WhenTheEmailIsFree() {
        given(userRepository.existsByEmail("jane@example.com")).willReturn(false);
        given(roleRepository.findByRoleName(Role.USER)).willReturn(Optional.of(userRole));
        given(passwordEncoder.encode("password123")).willReturn("{bcrypt}hash");
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        UserResponse response = userService.register(
                new RegisterRequest("Jane", "Doe", "  Jane@Example.COM ", "password123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("jane@example.com");
        assertThat(saved.getPassword()).isEqualTo("{bcrypt}hash");
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.isAccountLocked()).isFalse();
        assertThat(saved.getRoles()).containsExactly(userRole);
        assertThat(response).isEqualTo(new UserResponse(7L, "Jane", "Doe", "jane@example.com",
                Set.of(Role.USER)));
    }

    @Test
    void _02_ShouldRejectTheRegistration_WhenTheEmailIsAlreadyRegistered() {
        given(userRepository.existsByEmail("jane@example.com")).willReturn(true);

        assertThatThrownBy(() -> userService.register(
                new RegisterRequest("Jane", "Doe", "jane@example.com", "password123")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Email is already registered");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void _03_ShouldRejectTheRegistration_WhenAConcurrentInsertTakesTheEmail() {
        given(userRepository.existsByEmail("jane@example.com")).willReturn(false);
        given(roleRepository.findByRoleName(Role.USER)).willReturn(Optional.of(userRole));
        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(new DataIntegrityViolationException("users_email_key"));

        assertThatThrownBy(() -> userService.register(
                new RegisterRequest("Jane", "Doe", "jane@example.com", "password123")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void _04_ShouldReturnABearerToken_WhenTheCredentialsAreValid() {
        UserDetails principal = org.springframework.security.core.userdetails.User
                .withUsername("jane@example.com").password("x").roles(Role.USER).build();
        Authentication authenticated =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        given(authenticationManager.authenticate(any())).willReturn(authenticated);
        given(jwtService.generateToken(principal)).willReturn("signed.jwt.token");
        given(jwtService.getExpiration()).willReturn(Duration.ofHours(1));

        AuthResponse response = userService.login(new LoginRequest(" JANE@example.com", "password123"));

        assertThat(response).isEqualTo(new AuthResponse("signed.jwt.token", "Bearer", 3600));
        ArgumentCaptor<Authentication> captor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("jane@example.com");
        assertThat(captor.getValue().getCredentials()).isEqualTo("password123");
    }

    @Test
    void _05_ShouldPropagateTheFailureAndIssueNoToken_WhenTheCredentialsAreWrong() {
        given(authenticationManager.authenticate(any())).willThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> userService.login(new LoginRequest("jane@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void _06_ShouldReturnTheProfile_WhenTheAccountExists() {
        User jane = new User();
        jane.setId(3L);
        jane.setFirstName("Jane");
        jane.setLastName("Doe");
        jane.setEmail("jane@example.com");
        jane.getRoles().add(userRole);
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(jane));

        UserResponse response = userService.getCurrentUser("jane@example.com");

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.roles()).containsExactly(Role.USER);
    }

    @Test
    void _07_ShouldThrowNotFound_WhenTheAccountNoLongerExists() {
        given(userRepository.findByEmail("gone@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser("gone@example.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void _08_ShouldCreateAnAdminAccount_WhenNoAccountUsesTheEmail() {
        given(userRepository.existsByEmail("admin@example.com")).willReturn(false);
        given(roleRepository.findByRoleName(Role.ADMIN)).willReturn(Optional.of(adminRole));
        given(passwordEncoder.encode("admin-password")).willReturn("{bcrypt}admin");

        boolean created = userService.createAdminIfAbsent("Admin@Example.com", "admin-password");

        assertThat(created).isTrue();
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("admin@example.com");
        assertThat(captor.getValue().getPassword()).isEqualTo("{bcrypt}admin");
        assertThat(captor.getValue().getRoles()).containsExactly(adminRole);
        assertThat(captor.getValue().isEnabled()).isTrue();
    }

    @Test
    void _09_ShouldCreateNothing_WhenTheAdminAccountAlreadyExists() {
        given(userRepository.existsByEmail("admin@example.com")).willReturn(true);

        boolean created = userService.createAdminIfAbsent("admin@example.com", "admin-password");

        assertThat(created).isFalse();
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }
}
