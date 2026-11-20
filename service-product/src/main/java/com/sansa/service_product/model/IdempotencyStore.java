package com.sansa.service_product.model;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Stores the idempotency keys that have already been processed, avoiding duplicated operations on retries.
@Component
public class IdempotencyStore {
    private final Set<String> processedKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> restoredKeys = ConcurrentHashMap.newKeySet();

    public boolean alreadyProcessed(String key) { return processedKeys.contains(key); }
    public void markProcessed(String key)       { processedKeys.add(key); }

    public boolean alreadyRestored(String key)  { return restoredKeys.contains(key); }
    public void markRestored(String key)        { restoredKeys.add(key); }
}

