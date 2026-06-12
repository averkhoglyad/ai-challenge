package io.averkhogliad.ai.challenge.week1.domain.service

/**
 * Порт для управления ресурсами приложения.
 * 
 * Domain-абстракция для освобождения ресурсов (HTTP-соединения, пулы потоков).
 * Infrastructure-слой реализует этот интерфейс для конкретных клиентов.
 */
interface ResourceManager {
    /**
     * Освобождает ресурсы.
     * Вызывается при завершении работы приложения.
     */
    fun close()
}
