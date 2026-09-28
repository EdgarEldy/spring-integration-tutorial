package com.edgareldy.springintegrationtutorial.repository;

import com.edgareldy.springintegrationtutorial.entity.Order;
import com.edgareldy.springintegrationtutorial.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for {@link Order}.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Moves an order from one status to another, only if it currently has the expected status.
     *
     * @param id       the order
     * @param expected the status the order must have for the update to apply
     * @param target   the new status
     * @return 1 if the order was updated, 0 if it does not exist or no longer has the expected status
     */
    // A conditional UPDATE instead of "read, check, write": the check and the write are one statement, so
    // two administrators resolving the same order at the same moment cannot both succeed; the second one
    // updates no row. @Modifying marks the query as a write; clearAutomatically empties the persistence
    // context afterwards, so a later findById reads the new status instead of a stale cached entity.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = :target WHERE o.id = :id AND o.status = :expected")
    int updateStatusIfCurrent(@Param("id") Long id, @Param("expected") OrderStatus expected,
                              @Param("target") OrderStatus target);
}
