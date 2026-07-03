package io.averkhogliad.ai.challenge.week4.cli.domain.service

/**
 * Порт для управления ресурсами приложения.
 * 
 * Domain-абстракция для освобождения ресурсов (HTTP-соединения, пулы потоков).
 * Infrastructure-слой реализует этот интерфейс для конкретных клиентов.
 */
interface ResourceManager : AutoCloseable {

}
