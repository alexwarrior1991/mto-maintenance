package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.operation.OperationError;

/**
 * Traduce lo que lanzo una operacion de fondo a lo que habria respondido la llamada sincrona. Lo
 * implementa la capa web, que es la que sabe que estado HTTP y que codigo corresponden a cada
 * excepcion; asi la operacion no inventa una tabla aparte que acabaria separandose.
 */
public interface OperationErrorTranslator {

    OperationError translate(Throwable failure);
}
