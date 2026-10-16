package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.operation.OperationResponse;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Lo que tarda se vuelve asincrono: un trabajo corre en un hilo virtual y la peticion lo espera hasta
 * su presupuesto. Si termina antes, se responde lo de siempre; si no, {@code 202 Accepted} con la
 * operacion, que sigue, y {@code GET /operations/{id}} da su resultado al terminar.
 *
 * <p>El recurso escaso no es el hilo sino la conexion que el trabajo retiene mientras dura: por eso
 * hay un tope de operaciones vivas para todo el despliegue, contado en {@code background_operation} y
 * no en memoria (con tres replicas un semaforo seria el triple), y las del mismo ambito (un EP) corren
 * de una en una, esperando en {@code QUEUED} sin conexion.</p>
 */
public interface BudgetedOperations {

    /**
     * @param kind  que es ({@code GENERATE_TASKS}): se guarda y se devuelve
     * @param scope ambito en el que no pueden correr dos a la vez ({@code EP-14}); {@code null} si no hay
     * @param successStatus el estado HTTP de la llamada sincrona cuando va bien (200, 201): se guarda con
     *              el resultado para que {@code GET /operations/{id}} lo diga
     * @param wait  lo que la peticion pide esperar antes de contestar 202 ({@code Prefer: wait=N}), acotado a
     *              {@code app.operations.max-wait}; {@code null} espera {@code app.operations.default-wait}, y
     *              cero responde 202 en cuanto se apunta. A diferencia de la copia de mto-resources, el
     *              tope lo pone la operacion y no quien llama
     * @param work  el trabajo: abre sus propias transacciones, y lo que lance es el error de la operacion
     * @return el resultado si termino dentro del presupuesto, o la operacion pendiente
     * @throws RuntimeException lo que lanzo el trabajo si fallo dentro del presupuesto, tal cual, para
     *                          que la respuesta sea la misma que la de la llamada sincrona
     */
    <T> Outcome<T> run(String kind, String scope, int successStatus, Duration wait, Supplier<T> work);

    /**
     * La operacion, a quien la pidio, a quien supervisa ({@code maintenance-supervise}) o a quien opera el
     * servicio ({@code ops-write}); si no, 404.
     */
    OperationResponse find(UUID id);

    /** El latido de las operaciones vivas de este proceso: sin el, otra replica las daria por abandonadas. */
    void heartbeat();

    /** Las vivas sin latido en {@code app.operations.stale-after} pasan a {@code FAILED}: su replica murio. */
    int failAbandoned();

    /** Borra las terminadas hace mas de {@code app.operations.retention}. */
    int purgeFinished();

    /**
     * @param operation la operacion apuntada; siempre presente
     * @param result    el resultado, si termino dentro del presupuesto
     * @param completed si termino dentro del presupuesto
     */
    record Outcome<T>(OperationResponse operation, T result, boolean completed) {
    }
}
