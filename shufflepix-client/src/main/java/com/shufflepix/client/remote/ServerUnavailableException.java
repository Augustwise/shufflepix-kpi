package com.shufflepix.client.remote;

public class ServerUnavailableException extends RuntimeException {
    public ServerUnavailableException(Throwable cause) {
        super("Сервер недоступний. Перевірте, чи запущено серверний застосунок і його адресу в client.properties.", cause);
    }
}
