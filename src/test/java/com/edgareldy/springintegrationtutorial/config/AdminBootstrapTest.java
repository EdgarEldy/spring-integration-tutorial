package com.edgareldy.springintegrationtutorial.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.edgareldy.springintegrationtutorial.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * Unit tests of {@link AdminBootstrap}: the admin account is requested only when both variables are
 * set, and an existing account is not duplicated.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserService userService;

    @Test
    void _01_ShouldCreateTheAdminAccount_WhenBothVariablesAreSet() {
        given(userService.createAdminIfAbsent("admin@example.com", "s3cret-admin")).willReturn(true);

        run("admin@example.com", "s3cret-admin");

        verify(userService).createAdminIfAbsent("admin@example.com", "s3cret-admin");
    }

    @Test
    void _02_ShouldDoNothing_WhenTheVariablesAreAbsent() {
        run("", "");

        verifyNoInteractions(userService);
    }

    @Test
    void _03_ShouldDoNothing_WhenOnlyTheEmailIsSet() {
        run("admin@example.com", "");

        verifyNoInteractions(userService);
    }

    @Test
    void _04_ShouldDoNothing_WhenOnlyThePasswordIsSet() {
        run("  ", "s3cret-admin");

        verifyNoInteractions(userService);
    }

    @Test
    void _05_ShouldLeaveTheExistingAccount_WhenTheAdminAlreadyExists() {
        // createAdminIfAbsent answers false when the email is taken: the bootstrap only logs it, so a
        // restart with the same variables never creates a second account.
        given(userService.createAdminIfAbsent("admin@example.com", "s3cret-admin")).willReturn(false);

        run("admin@example.com", "s3cret-admin");

        verify(userService).createAdminIfAbsent("admin@example.com", "s3cret-admin");
    }

    private void run(String email, String password) {
        new AdminBootstrap(userService, email, password).run(new DefaultApplicationArguments());
    }
}
