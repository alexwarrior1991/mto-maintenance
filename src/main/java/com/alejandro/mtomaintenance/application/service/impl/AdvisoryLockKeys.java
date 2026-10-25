package com.alejandro.mtomaintenance.application.service.impl;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * La clave de un cerrojo de aviso de PostgreSQL ({@code pg_advisory_xact_lock(bigint)}) a partir de su
 * nombre ({@code mto-maintenance:generate-tasks:EP-6}): los 64 bits altos de su SHA-256.
 *
 * <p>Todos los cerrojos de aviso de la base comparten un solo espacio de claves. Con el
 * {@code String.hashCode()} de 32 bits que se usaba, dos nombres distintos podian dar la misma clave
 * (con miles de recursos de turno, la probabilidad deja de ser despreciable): dos trabajos sin nada que
 * ver se esperarian, y dos turnos que bloquean sus recursos en orden podian cruzarse en un interbloqueo.
 * Con 64 bits eso no pasa en la practica, y quien toma varios los toma ordenados por la clave, no por
 * el nombre.</p>
 */
final class AdvisoryLockKeys {

    private AdvisoryLockKeys() {
    }

    static long of(String name) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is always available", impossible);
        }
    }
}
