package com.example.devops;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
public class ApiController {

    @Value("${app.message:Hello, World!}")
    private String appMessage;

    @Value("${app.env:local}")
    private String appEnv;

    @GetMapping("/api/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("message", appMessage);
        status.put("environment", appEnv);
        status.put("timestamp", LocalDateTime.now().toString());
        
        try {
            status.put("hostname", InetAddress.getLocalHost().getHostName());
            status.put("hostAddress", InetAddress.getLocalHost().getHostAddress());
        } catch (UnknownHostException e) {
            status.put("hostname", "unknown-host");
            status.put("hostAddress", "unknown-ip");
        }
        
        return status;
    }

    @GetMapping("/api/env")
    public Map<String, String> getEnvVars() {
        Map<String, String> importantEnvs = new HashMap<>();
        importantEnvs.put("PATH", System.getenv("PATH"));
        importantEnvs.put("JAVA_VERSION", System.getenv("JAVA_VERSION"));
        importantEnvs.put("APP_ENV", System.getenv("APP_ENV"));
        importantEnvs.put("APP_MESSAGE", System.getenv("APP_MESSAGE"));
        importantEnvs.put("POD_NAMESPACE", System.getenv("POD_NAMESPACE"));
        importantEnvs.put("POD_IP", System.getenv("POD_IP"));
        return importantEnvs;
    }
}
