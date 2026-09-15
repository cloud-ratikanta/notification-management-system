package com.interview.assessment.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Iterator;

@Component
public class AuditSanitizer {

    private final ObjectMapper mapper;

    public AuditSanitizer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String sanitize(String payloadJson) {
        if (payloadJson == null) return null;
        try {
            JsonNode node = mapper.readTree(payloadJson);
            sanitizeNode(node);
            // compact string
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            // fallback: redact common secrets by regex
            String s = payloadJson.replaceAll("(?i)\\b(token|secret|authorization)\\b\\s*[:=]\\s*\"[^\"]+\"", "$1:\"<REDACTED>\"");
            if (s.length() > 1024) return s.substring(0, 1024) + "...";
            return s;
        }
    }

    private void sanitizeNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<String> it = obj.fieldNames();
            while (it.hasNext()) {
                String name = it.next();
                String lower = name.toLowerCase();
                if (lower.contains("token") || lower.contains("secret") || lower.contains("authorization") || lower.contains("auth")) {
                    obj.put(name, "<REDACTED>");
                    continue;
                }
                JsonNode child = obj.get(name);
                if (child.isTextual() && (name.equalsIgnoreCase("body") || name.equalsIgnoreCase("message"))) {
                    String v = child.asText();
                    if (v.length() > 256) {
                        obj.put(name, "<REDACTED_BODY length=" + v.length() + ">");
                    }
                } else {
                    sanitizeNode(child);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode el : node) sanitizeNode(el);
        }
    }
}

