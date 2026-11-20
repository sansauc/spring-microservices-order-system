package com.sansa.service_product.utils.errors;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, List<String>>errors) {
}
