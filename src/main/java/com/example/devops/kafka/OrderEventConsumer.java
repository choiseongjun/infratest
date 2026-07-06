package com.example.devops.kafka;

import com.example.devops.model.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Component
public class OrderEventConsumer {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public static volatile boolean delayEnabled = false;

    @Value("${app.opensearch.url}")
    private String opensearchUrl;

    public OrderEventConsumer(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopics = "true",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_DELAY_VALUE,
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "order-events", groupId = "devops-group")
    public void consume(String message) {
        if (delayEnabled) {
            try {
                System.out.println("Kafka Consumer: Simulating processing delay (5s)...");
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Kafka Consumer: Received order-event: " + message);
        try {
            Order order = objectMapper.readValue(message, Order.class);

            // DLQ 유입 유도를 위한 인위적인 예외 발생 조건
            if ("FAIL-DLQ".equals(order.getProductName()) || order.getPrice() < 0) {
                throw new IllegalArgumentException("[DLQ Test] Invalid order detected! Product: " 
                        + order.getProductName() + ", Price: " + order.getPrice());
            }

            // 1. Redis 캐시 업데이트 (Key: order:<id>, TTL: 10분)
            String redisKey = "order:" + order.getId();
            redisTemplate.opsForValue().set(redisKey, message, Duration.ofMinutes(10));
            System.out.println("Redis Cache Updated: " + redisKey);

            // 2. Opensearch에 색인(Index) 생성
            indexInOpensearch(order, message);

        } catch (IllegalArgumentException e) {
            // DLQ 처리를 위해 런타임 예외를 상위로 다시 던짐
            System.err.println("Kafka Consumer: Invalid order exception thrown. Triggering Kafka retry...");
            throw e;
        } catch (Exception e) {
            System.err.println("Kafka Consumer: Error processing message: " + e.getMessage());
            throw new RuntimeException("Message processing failed", e);
        }
    }

    @DltHandler
    public void handleDlt(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        System.err.println("=================================================");
        System.err.println("🚨 [DLT Consumer] Message routed to Dead Letter Topic: " + topic);
        System.err.println("🚨 Failed Message Payload: " + message);
        System.err.println("=================================================");
        
        // 여기에 DLT 적재 기록을 DB에 저장하거나 Slack 알림 등을 전송하는 추가 로직이 들어갈 수 있습니다.
        try {
            Order order = objectMapper.readValue(message, Order.class);
            String redisKey = "order:dlt:" + order.getId();
            redisTemplate.opsForValue().set(redisKey, message, Duration.ofHours(24));
            System.out.println("DLT Record saved in Redis for tracking: " + redisKey);
        } catch (Exception e) {
            System.err.println("Failed to write DLT event to Redis: " + e.getMessage());
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
            System.err.println("Opensearch Indexing Failed: " + e.getMessage());
        }
    }
}

