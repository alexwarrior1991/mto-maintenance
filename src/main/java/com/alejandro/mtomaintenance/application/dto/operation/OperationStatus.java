package com.alejandro.mtomaintenance.application.dto.operation;

/** Estado de una operacion de fondo. */
public enum OperationStatus {

    /** Apuntada, esperando a que acabe otra del mismo ambito (un EP). Espera sin conexion. */
    QUEUED,

    /** En marcha en un hilo virtual. */
    RUNNING,

    /** Termino bien: {@code result} es el cuerpo de la llamada sincrona. */
    SUCCEEDED,

    /** Termino mal, o se abandono (su latido se paro): {@code error} dice por que. */
    FAILED;

    public boolean isFinished() {
        return this == SUCCEEDED || this == FAILED;
    }
}
