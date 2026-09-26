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
 * A product category of the commerce domain. Nothing in the API creates one: categories come from the
 * demo data in the dev profile, or from the tests.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
// A JPA entity maps a class onto a table created by Flyway (V1__init_schema.sql). With ddl-auto:
// validate, Hibernate never changes the schema: it only checks at startup that every mapped column
// exists with a compatible type, so a mapping mistake fails fast instead of altering the database.
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class Category {

    // IDENTITY lets PostgreSQL assign the id from the BIGSERIAL column, the way V1 declares it.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_name", nullable = false, unique = true, length = 100)
    private String categoryName;
}
