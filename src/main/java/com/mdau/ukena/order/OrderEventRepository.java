package com.mdau.ukena.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderEventRepository extends JpaRepository<OrderEvent, UUID> {
    List<OrderEvent> findByOrderIdOrderByAtAsc(UUID orderId);

    List<OrderEvent> findByOrderIdInOrderByAtAsc(Collection<UUID> orderIds);
}
