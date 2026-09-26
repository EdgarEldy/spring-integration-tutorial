package com.edgareldy.springintegrationtutorial.dto.auth;

import com.edgareldy.springintegrationtutorial.entity.Role;
import com.edgareldy.springintegrationtutorial.entity.User;
import java.util.Set;
import java.util.TreeSet;

/**
 * Public view of an account, returned by registration and by {@code GET /api/v1/auth/me}. The password
 * hash and the internal flags never leave the server.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 *
 * @param id        the account id
 * @param firstName the first name
 * @param lastName  the last name
 * @param email     the email
 * @param roles     the role names, sorted
 */
public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        Set<String> roles
) {

    /**
     * @param user the entity
     * @return its public view
     */
    public static UserResponse from(User user) {
        Set<String> roles = new TreeSet<>();
        user.getRoles().stream().map(Role::getRoleName).forEach(roles::add);
        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), roles);
    }
}
