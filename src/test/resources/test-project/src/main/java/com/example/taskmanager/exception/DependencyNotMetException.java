package com.example.taskmanager.exception;

public class DependencyNotMetException extends RuntimeException {

    public DependencyNotMetException(String message) {
        super(message);
    }
}
