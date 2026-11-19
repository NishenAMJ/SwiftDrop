package com.swiftdrop.payment.cod.service;

public class InvalidCodEvent extends RuntimeException {
    public InvalidCodEvent(String message) { super(message); }
}
