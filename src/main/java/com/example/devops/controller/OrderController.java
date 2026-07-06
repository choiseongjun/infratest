package com.example.devops.controller;

import com.example.devops.model.Order;
import com.example.devops.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // 1. 주문 등록 API
    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
        Order savedOrder = orderService.createOrder(order);
        return new ResponseEntity<>(savedOrder, HttpStatus.CREATED);
    }

    // 2. 주문 단건 조회 API (Redis 캐시 체크 적용)
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id)
                .map(order -> new ResponseEntity<>(order, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    // 3. 주문 검색 API (Opensearch 연동)
    @GetMapping("/search")
    public ResponseEntity<String> searchOrders(@RequestParam String keyword) {
        String searchResult = orderService.searchOrders(keyword);
        return ResponseEntity.ok(searchResult);
    }

    // 4. 통계 집계 API (PostgreSQL 복잡한 집계 쿼리 실행)
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> statistics = orderService.getOrderStatistics();
        return ResponseEntity.ok(statistics);
    }
}
