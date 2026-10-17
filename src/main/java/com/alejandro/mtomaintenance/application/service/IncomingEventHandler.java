package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.messaging.IncomingEvent;
import com.alejandro.mtomaintenance.application.dto.messaging.IncomingEventContext;

/**
 * Lo que hace este servicio con los eventos de una entidad de otro servicio: un {@code @Service} por
 * entidad, recogido del contexto sin registrar nada, como los manejadores de datos maestros
 * ({@link MasterDataEntityHandler}). Dos para la misma entidad y el mismo emisor impiden arrancar; una entidad
 * sin manejador se registra y se ignora.
 *
 * <p>Corre dentro de la transaccion del inbox: no abre la suya ni llama a otro servicio.</p>
 */
public interface IncomingEventHandler {

    /** El servicio emisor, el {@code origin} del sobre ({@code mto-resources}). */
    String sourceService();

    /** La entidad de sus eventos que atiende ({@code team}). */
    String entityName();

    void handle(IncomingEvent event, IncomingEventContext context);
}
