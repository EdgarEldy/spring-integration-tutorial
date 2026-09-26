package com.edgareldy.springintegrationtutorial.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springintegrationtutorial.TestcontainersConfiguration;
import com.edgareldy.springintegrationtutorial.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Checks that {@link RoleRepository} finds the two roles seeded by the V1 migration.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RoleRepositoryTest {

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void _01_ShouldFindTheSeededRoles_WhenLookedUpByName() {
        assertThat(roleRepository.findByRoleName(Role.ADMIN)).isPresent();
        assertThat(roleRepository.findByRoleName(Role.USER)).isPresent();
    }

    @Test
    void _02_ShouldReturnEmpty_WhenTheRoleIsNotSeeded() {
        assertThat(roleRepository.findByRoleName("MANAGER")).isEmpty();
    }
}
