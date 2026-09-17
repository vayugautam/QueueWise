package com.queuewise.audit.application;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class SafeAuditMetadata {

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "password", "passwordhash", "token", "accesstoken", "refreshtoken",
            "authorization", "jwt", "secret", "privatekey", "clientsecret");

    private SafeAuditMetadata() { }

    static Map<String, Object> sanitize(Map<String, Object> metadata) {
        return metadata.entrySet().stream()
                .filter(entry -> !FORBIDDEN_KEYS.contains(normalize(entry.getKey())))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static String normalize(String key) {
        return key == null ? "" : key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}