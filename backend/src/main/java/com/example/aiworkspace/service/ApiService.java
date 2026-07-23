package com.example.aiworkspace.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ApiService {

    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("service", "Spring Boot Backend API");
        status.put("version", "3.3.0");
        status.put("javaVersion", System.getProperty("java.version"));
        return status;
    }
}
