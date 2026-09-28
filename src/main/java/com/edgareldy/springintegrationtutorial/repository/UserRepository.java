package com.edgareldy.springintegrationtutorial.repository;

import com.edgareldy.springintegrationtutorial.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access to the accounts, looked up by their (normalized, lower-case) email.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
// Spring Data derives the queries from the method names (findBy..., existsBy...): no implementation
// class is written, the repository proxy is generated at startup.
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * @param email the normalized email
     * @return the account, if any
     */
    Optional<User> findByEmail(String email);

    /**
     * @param email the normalized email
     * @return whether an account already uses this email
     */
    boolean existsByEmail(String email);
}
