package com.sansa.service_product.utils.exceptions;

public class InsufficientStockException extends RuntimeException{

    public InsufficientStockException(String message) {
        super(message);
    }

}
