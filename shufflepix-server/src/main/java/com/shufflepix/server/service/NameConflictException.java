package com.shufflepix.server.service;

public class NameConflictException extends RuntimeException {
    public NameConflictException(String name) {
        super("Проєкт з назвою «" + name + "» вже існує");
    }
}
