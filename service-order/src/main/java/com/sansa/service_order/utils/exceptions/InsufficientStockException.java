package com.sansa.service_order.utils.exceptions;

public class InsufficientStockException extends RuntimeException{

    public InsufficientStockException(String message) {
        super(message);
    }

}
