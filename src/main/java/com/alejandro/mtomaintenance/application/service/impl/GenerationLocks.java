package com.alejandro.mtomaintenance.application.service.impl;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * La fila de las generaciones de tareas de un mismo ambito ({@code EP-<paquete>}, u {@code order-<id>}
 * en una orden sin EP) dentro de esta instancia. La que espera lo hace aqui, antes de abrir su
 * transaccion, asi que no retiene ninguna conexion de Hikari; con el turno, abre la transaccion y toma
 * el cerrojo de aviso de PostgreSQL, que en esta instancia ya esta libre.
 *
 * <p>Solo ve esta JVM: lo que protege entre instancias es ese cerrojo de aviso
 * ({@code MaintenanceTaskRepository.advisoryTransactionLock}), que no se quita. El cerrojo es justo, para
 * que la fila vaya por orden de llegada, y un {@link ReentrantLock} no clava el hilo virtual que espera.
 * Hay uno por ambito y no se borran: los EP son pocos, y sin EP es uno por orden, unos bytes.</p>
 */
@Component
class GenerationLocks {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /** Ejecuta {@code work} con el turno de {@code scope}, esperando a que lo suelte quien lo tenga. */
    <T> T withLock(String scope, Supplier<T> work) {
        ReentrantLock lock = locks.computeIfAbsent(scope, key -> new ReentrantLock(true));
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to generate the tasks of " + scope, interrupted);
        }
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }
}
