package com.edgareldy.springintegrationtutorial.repository;

import com.edgareldy.springintegrationtutorial.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link Product}.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
public interface ProductRepository extends JpaRepository<Product, Long> {
}
