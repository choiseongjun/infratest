package com.example.devops.kafka;

import com.example.devops.model.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendOrderEvent(Order order) {
        try {
            String message = objectMapper.writeValueAsString(order);
            kafkaTemplate.send("order-events", order.getId().toString(), message);
            System.out.println("Kafka Producer: Sent order-event for ID " + order.getId());
        } catch (JsonProcessingException e) {
            System.err.println("Kafka Producer: Failed to serialize order: " + e.getMessage());
        }
    }
}
