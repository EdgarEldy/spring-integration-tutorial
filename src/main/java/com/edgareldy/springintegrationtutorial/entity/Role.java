package com.edgareldy.springintegrationtutorial.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A role of the role-only authorization model ({@code ADMIN} or {@code USER}), seeded by the V1
 * migration and never created by the application.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
// Hibernate only validates this mapping against the Flyway schema (ddl-auto: validate): every column
// name and type here must match the roles table of V1__init_schema.sql.
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    /** Name of the role granted to every registered account. */
    public static final String USER = "USER";

    /** Name of the role required by the order review endpoints. */
    public static final String ADMIN = "ADMIN";

    @Id
    // IDENTITY lets PostgreSQL's BIGSERIAL assign the key on insert, as declared in the migration.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_name", nullable = false, unique = true, length = 50)
    private String roleName;

    /**
     * @param roleName the role name, without the {@code ROLE_} prefix Spring Security adds
     */
    public Role(String roleName) {
        this.roleName = roleName;
    }
}
