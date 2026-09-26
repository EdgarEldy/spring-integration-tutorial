package com.edgareldy.springintegrationtutorial.config;

import com.edgareldy.springintegrationtutorial.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates the ADMIN account at startup from {@code APP_ADMIN_EMAIL} and {@code APP_ADMIN_PASSWORD}
 * ({@code app.admin.email} / {@code app.admin.password}) when both are set and the account does not
 * exist yet. Without them nothing happens: there is no default admin password anywhere.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
// An ApplicationRunner runs once the context is fully started, after Flyway has migrated the schema
// and seeded the roles, which is exactly when the account can be inserted.
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserService userService;
    private final String email;
    private final String password;

    /**
     * @param userService creates the account
     * @param email       the admin email, empty when not configured
     * @param password    the admin password, empty when not configured
     */
    public AdminBootstrap(UserService userService,
                          @Value("${app.admin.email:}") String email,
                          @Value("${app.admin.password:}") String password) {
        this.userService = userService;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            log.info("APP_ADMIN_EMAIL and APP_ADMIN_PASSWORD are not both set: no admin account created");
            return;
        }
        if (userService.createAdminIfAbsent(email, password)) {
            log.info("Admin account {} created", email);
        } else {
            // Restarting with the same variables must be harmless: the existing account (and a password
            // possibly changed since) is left untouched.
            log.info("Admin account {} already exists, left unchanged", email);
        }
    }
}
