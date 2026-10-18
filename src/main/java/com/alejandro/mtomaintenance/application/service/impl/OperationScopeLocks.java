package com.alejandro.mtomaintenance.application.service.impl;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * La fila de las operaciones de fondo de un mismo ambito dentro de esta instancia. La que espera lo hace
 * aqui, sin conexion y sin preguntar a la base de datos; con el turno local pide el de la base de datos
 * ({@code startIfTurn}) y, como la anterior de la instancia ya termino y lo dejo apuntado, le toca a la
 * primera.
 *
 * <p>Solo ve esta JVM: entre replicas manda el turno de {@code background_operation}, que no se quita.
 * El cerrojo es justo, para que la fila vaya por orden de llegada, y un {@link ReentrantLock} no clava el
 * hilo virtual que espera. Hay uno por ambito y no se borran: los ambitos son pocos.</p>
 *
 * <p>Va aparte de {@link GenerationLocks} a proposito: una generacion con {@code respond-async} toma
 * primero este turno, el de la operacion, y luego, dentro de su trabajo, el de la generacion; una sincrona
 * solo el de la generacion. Siempre en ese orden, asi que no se esperan la una a la otra en circulo.</p>
 */
@Component
class OperationScopeLocks {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /** Ejecuta {@code work} con el turno local de {@code scope}, esperando a que lo suelte quien lo tenga. */
    <T> T withLock(String scope, Supplier<T> work) {
        ReentrantLock lock = locks.computeIfAbsent(scope, key -> new ReentrantLock(true));
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the turn of the background operations of " + scope, interrupted);
        }
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }
}
