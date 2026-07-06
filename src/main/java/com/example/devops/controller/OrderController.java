package com.example.devops.controller;

import com.example.devops.kafka.OrderEventConsumer;
import com.example.devops.model.Order;
import com.example.devops.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private static final List<byte[]> leakList = new ArrayList<>();

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

    // 5. [장애 시나리오] OOM 유발 API
    @PostMapping("/simulate-oom")
    public ResponseEntity<Map<String, Object>> simulateOom() {
        System.out.println("🚨 OOM Simulation triggered! Allocating 50MB of heap...");
        // 50MB씩 할당
        for (int i = 0; i < 5; i++) {
            leakList.add(new byte[10 * 1024 * 1024]);
        }
        
        long freeMem = Runtime.getRuntime().freeMemory();
        long totalMem = Runtime.getRuntime().totalMemory();
        long maxMem = Runtime.getRuntime().maxMemory();
        
        Map<String, Object> memStatus = new HashMap<>();
        memStatus.put("message", "Allocated 50MB. Cumulative leak count: " + leakList.size() + " blocks.");
        memStatus.put("freeMemoryMB", freeMem / (1024 * 1024));
        memStatus.put("totalMemoryMB", totalMem / (1024 * 1024));
        memStatus.put("maxMemoryMB", maxMem / (1024 * 1024));
        
        System.out.println("🚨 Memory Status: " + memStatus);
        return ResponseEntity.ok(memStatus);
    }

    // 6. [장애 시나리오] 컨슈머 딜레이 토글 API
    @PostMapping("/simulate-delay")
    public ResponseEntity<Map<String, Object>> toggleDelay(@RequestParam(defaultValue = "true") boolean enable) {
        OrderEventConsumer.delayEnabled = enable;
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Kafka Consumer processing delay has been " + (enable ? "ENABLED (5 seconds)" : "DISABLED"));
        response.put("delayEnabled", OrderEventConsumer.delayEnabled);
        return ResponseEntity.ok(response);
    }

    // 7. [장애 시나리오] 딜레이 상태 확인 API
    @GetMapping("/simulate-delay")
    public ResponseEntity<Map<String, Object>> getDelayStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("delayEnabled", OrderEventConsumer.delayEnabled);
        return ResponseEntity.ok(response);
    }

    // 8. [모니터링 API] 실시간 JVM 메모리 상태 제공
    @GetMapping("/memory")
    public ResponseEntity<Map<String, Object>> getMemoryStatus() {
        long freeMem = Runtime.getRuntime().freeMemory();
        long totalMem = Runtime.getRuntime().totalMemory();
        long maxMem = Runtime.getRuntime().maxMemory();
        long usedMem = totalMem - freeMem;
        double usedPercentage = ((double) usedMem / maxMem) * 100.0;

        Map<String, Object> memStatus = new HashMap<>();
        memStatus.put("freeMemoryMB", freeMem / (1024 * 1024));
        memStatus.put("totalMemoryMB", totalMem / (1024 * 1024));
        memStatus.put("maxMemoryMB", maxMem / (1024 * 1024));
        memStatus.put("usedMemoryMB", usedMem / (1024 * 1024));
        memStatus.put("usedPercentage", Math.round(usedPercentage * 10.0) / 10.0);
        return ResponseEntity.ok(memStatus);
    }

    // 9. [모니터링 API] Redis DLT 백업 목록 조회
    @GetMapping("/dlt")
    public ResponseEntity<List<String>> getDltList() {
        return ResponseEntity.ok(orderService.getDltRawMessages());
    }

    // 10. [모니터링 API] Redis DLT 백업 목록 초기화
    @PostMapping("/dlt/clear")
    public ResponseEntity<Map<String, String>> clearDltList() {
        orderService.clearDltMessages();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Redis DLT backup storage has been cleared successfully.");
        return ResponseEntity.ok(response);
    }
}
