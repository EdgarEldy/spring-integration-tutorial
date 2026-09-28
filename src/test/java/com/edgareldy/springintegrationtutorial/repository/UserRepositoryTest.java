package com.edgareldy.springintegrationtutorial.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edgareldy.springintegrationtutorial.TestcontainersConfiguration;
import com.edgareldy.springintegrationtutorial.entity.Role;
import com.edgareldy.springintegrationtutorial.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Checks the {@link User} mapping and the derived queries of {@link UserRepository} against the real
 * PostgreSQL schema created by Flyway.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
// @DataJpaTest starts only the JPA slice (entities, repositories, Flyway) and rolls every test back.
// By default it would swap the datasource for an embedded database; replace = NONE keeps the
// Testcontainers PostgreSQL, so the mapping is validated against the real migration.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void _01_ShouldPersistTheUserWithItsRoles_WhenSaved() {
        User saved = userRepository.saveAndFlush(user("jane@example.com", Role.USER));

        User found = userRepository.findByEmail("jane@example.com").orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.isEnabled()).isTrue();
        assertThat(found.isAccountLocked()).isFalse();
        assertThat(found.getRoles()).extracting(Role::getRoleName).containsExactly(Role.USER);
    }

    @Test
    void _02_ShouldReportTheEmailAsTaken_WhenAnAccountUsesIt() {
        userRepository.saveAndFlush(user("taken@example.com", Role.USER));

        assertThat(userRepository.existsByEmail("taken@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("free@example.com")).isFalse();
    }

    @Test
    void _03_ShouldReturnEmpty_WhenNoAccountHasTheEmail() {
        assertThat(userRepository.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void _04_ShouldRejectTheInsert_WhenTheEmailIsAlreadyUsed() {
        userRepository.saveAndFlush(user("twice@example.com", Role.USER));

        assertThatThrownBy(() -> userRepository.saveAndFlush(user("twice@example.com", Role.ADMIN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User user(String email, String roleName) {
        User user = new User();
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setEmail(email);
        user.setPassword("{noop}secret");
        user.getRoles().add(roleRepository.findByRoleName(roleName).orElseThrow());
        return user;
    }
}
