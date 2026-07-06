package com.example.devops.service;

import com.example.devops.kafka.OrderEventProducer;
import com.example.devops.model.*;
import com.example.devops.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.opensearch.url}")
    private String opensearchUrl;

    public OrderService(OrderRepository orderRepository, OrderEventProducer orderEventProducer,
                        StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.orderEventProducer = orderEventProducer;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // 1. 주문 생성 (Postgres 쓰기 -> Kafka 이벤트 발행)
    @Transactional
    public Order createOrder(Order order) {
        if (order.getOrderDate() == null) {
            order.setOrderDate(LocalDateTime.now());
        }
        if (order.getStatus() == null) {
            order.setStatus(OrderStatus.CREATED);
        }
        
        // Postgres 저장
        Order savedOrder = orderRepository.save(order);

        // Kafka로 비동기 전송
        orderEventProducer.sendOrderEvent(savedOrder);

        return savedOrder;
    }

    // 2. 주문 단건 조회 (Redis Cache-Aside 패턴 적용)
    public Optional<Order> getOrderById(Long id) {
        String redisKey = "order:" + id;
        
        // 2.1 Redis 캐시 확인
        String cachedJson = redisTemplate.opsForValue().get(redisKey);
        if (cachedJson != null) {
            try {
                Order cachedOrder = objectMapper.readValue(cachedJson, Order.class);
                System.out.println("Redis Cache Hit for Order ID: " + id);
                return Optional.of(cachedOrder);
            } catch (Exception e) {
                System.err.println("Failed to parse cached order: " + e.getMessage());
            }
        }

        // 2.2 Redis 캐시 미스 시 Postgres DB 조회
        System.out.println("Redis Cache Miss. Querying DB for Order ID: " + id);
        Optional<Order> dbOrder = orderRepository.findById(id);
        
        // 2.3 DB 조회 결과를 Redis 캐시에 저장 (TTL 10분)
        dbOrder.ifPresent(order -> {
            try {
                String jsonStr = objectMapper.writeValueAsString(order);
                redisTemplate.opsForValue().set(redisKey, jsonStr, Duration.ofMinutes(10));
            } catch (Exception e) {
                System.err.println("Failed to cache order to Redis: " + e.getMessage());
            }
        });

        return dbOrder;
    }

    // 3. Opensearch를 이용한 고속 키워드 검색
    public String searchOrders(String keyword) {
        try {
            String url = opensearchUrl + "/orders/_search";

            // Opensearch Match Query DSL 작성
            String dslQuery = "{"
                    + "  \"query\": {"
                    + "    \"multi_match\": {"
                    + "      \"query\": \"" + keyword + "\","
                    + "      \"fields\": [\"productName\", \"customerName\"]"
                    + "    }"
                    + "  }"
                    + "}";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(dslQuery, headers);

            return restTemplate.postForObject(url, entity, String.class);
        } catch (Exception e) {
            return "{\"error\": \"Opensearch search failed: " + e.getMessage() + "\"}";
        }
    }

    // 4. 복잡한 통계 쿼리 실행 (Postgresql 직접 쿼리)
    public Map<String, Object> getOrderStatistics() {
        Map<String, Object> statistics = new HashMap<>();

        // 4.1 일별 매출 통계
        List<DailySales> dailySales = orderRepository.getDailySales();
        statistics.put("dailySales", dailySales);

        // 4.2 베스트셀러 상품
        List<TopProduct> topProducts = orderRepository.getTopProducts();
        statistics.put("topProducts", topProducts);

        // 4.3 주문 상태 분포
        List<StatusCount> statusDistribution = orderRepository.getStatusDistribution();
        statistics.put("statusDistribution", statusDistribution);

        statistics.put("queriedAt", LocalDateTime.now().toString());

        return statistics;
    }
}
