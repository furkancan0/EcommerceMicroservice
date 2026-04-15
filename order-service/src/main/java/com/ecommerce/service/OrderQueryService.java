package com.ecommerce.service;

import com.ecommerce.entity.Order;
import com.ecommerce.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepo;

    @Transactional(readOnly = true)
    public Optional<Order> findOrder(UUID orderId, UUID userId) {
        return orderRepo.findById(orderId)
            .filter(o -> o.getUserId().equals(userId));
    }

    @Transactional(readOnly = true)
    public Page<Order> listOrdersByUser(UUID userId, int page, int size) {
        return orderRepo.findByUserId(
            userId,
            PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }
}
