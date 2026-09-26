package com.edgareldy.springintegrationtutorial.repository;

import com.edgareldy.springintegrationtutorial.entity.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access to the roles seeded by the V1 migration.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * @param roleName {@code ADMIN} or {@code USER}
     * @return the role, if seeded
     */
    Optional<Role> findByRoleName(String roleName);
}
