package com.edgareldy.springintegrationtutorial.repository;

import com.edgareldy.springintegrationtutorial.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link Customer}.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
