package com.edgareldy.springintegrationtutorial.repository;

import com.edgareldy.springintegrationtutorial.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link Category}.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
// Spring Data JPA generates the implementation of this interface at startup: extending JpaRepository
// is enough to get save, findById, findAll, delete... without writing any query.
public interface CategoryRepository extends JpaRepository<Category, Long> {
}
