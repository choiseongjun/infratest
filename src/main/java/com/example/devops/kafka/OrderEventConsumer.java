package com.example.devops.kafka;

import com.example.devops.model.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Component
public class OrderEventConsumer {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.opensearch.url}")
    private String opensearchUrl;

    public OrderEventConsumer(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order-events", groupId = "devops-group")
    public void consume(String message) {
        System.out.println("Kafka Consumer: Received order-event: " + message);
        try {
            Order order = objectMapper.readValue(message, Order.class);

            // 1. Redis 캐시 업데이트 (Key: order:<id>, TTL: 10분)
            String redisKey = "order:" + order.getId();
            redisTemplate.opsForValue().set(redisKey, message, Duration.ofMinutes(10));
            System.out.println("Redis Cache Updated: " + redisKey);

            // 2. Opensearch에 색인(Index) 생성
            indexInOpensearch(order, message);

        } catch (Exception e) {
            System.err.println("Kafka Consumer: Error processing message: " + e.getMessage());
        }
    }

    private void indexInOpensearch(Order order, String jsonMessage) {
        try {
            String url = opensearchUrl + "/orders/_doc/" + order.getId();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(jsonMessage, headers);

            restTemplate.put(url, entity);
            System.out.println("Opensearch Indexing Success: ID " + order.getId());
        } catch (Exception e) {
            // Opensearch 색인이 실패하더라도 비즈니스 로직(DB 저장/캐시)에는 영향을 주지 않도록 예외 처리
            System.err.println("Opensearch Indexing Failed: " + e.getMessage());
        }
    }
}
