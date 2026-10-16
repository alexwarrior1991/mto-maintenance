package com.alejandro.mtomaintenance.application.dto.operation;

/**
 * Por que fallo una operacion de fondo: el mismo estado HTTP, codigo y mensaje que habria dado la
 * llamada sincrona.
 */
public record OperationError(int status, String errorCode, String message) {
}
