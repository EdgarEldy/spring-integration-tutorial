package com.edgareldy.springintegrationtutorial.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Unit tests of {@link JwtAuthFilter}: which requests end up authenticated, and that every request
 * continues down the chain whatever its token.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    private JwtAuthFilter filter;
    private MockHttpServletRequest request;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtService, userDetailsService);
        request = new MockHttpServletRequest();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void _01_ShouldAuthenticateTheCaller_WhenTheTokenIsValidAndTheAccountActive() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer good-token");
        given(jwtService.extractEmail("good-token")).willReturn("jane@example.com");
        given(userDetailsService.loadUserByUsername("jane@example.com")).willReturn(account(true, false));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo("jane@example.com");
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void _02_ShouldLeaveTheRequestAnonymous_WhenThereIsNoAuthorizationHeader() throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService, userDetailsService);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void _03_ShouldLeaveTheRequestAnonymous_WhenTheHeaderIsNotABearerToken() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic amFuZTpzZWNyZXQ=");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void _04_ShouldLeaveTheRequestAnonymousAndContinue_WhenTheTokenIsInvalid() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bad-token");
        given(jwtService.extractEmail("bad-token")).willThrow(new JwtException("bad signature"));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void _05_ShouldLeaveTheRequestAnonymous_WhenTheAccountNoLongerExists() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer orphan-token");
        given(jwtService.extractEmail("orphan-token")).willReturn("gone@example.com");
        given(userDetailsService.loadUserByUsername("gone@example.com"))
                .willThrow(new UsernameNotFoundException("gone"));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void _06_ShouldLeaveTheRequestAnonymous_WhenTheAccountIsLocked() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer locked-token");
        given(jwtService.extractEmail("locked-token")).willReturn("jane@example.com");
        given(userDetailsService.loadUserByUsername("jane@example.com")).willReturn(account(true, true));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void _07_ShouldLeaveTheRequestAnonymous_WhenTheAccountIsDisabled() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer disabled-token");
        given(jwtService.extractEmail("disabled-token")).willReturn("jane@example.com");
        given(userDetailsService.loadUserByUsername("jane@example.com")).willReturn(account(false, false));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static UserDetails account(boolean enabled, boolean locked) {
        return User.withUsername("jane@example.com").password("x").roles("USER")
                .disabled(!enabled).accountLocked(locked).build();
    }
}
