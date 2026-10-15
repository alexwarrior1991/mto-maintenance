package com.alejandro.mtomaintenance.application.dto.messaging;

import java.util.Map;

/**
 * Business payload of a master data change published by {@code mto-configuration}.
 *
 * <p>Es el {@code data} del sobre {@link MasterDataChangedMessage}, y es copia fiel del record
 * {@code MasterDataChangedEvent} de {@code mto-configuration}: mismos nombres de campo, mismo
 * orden, mismos tipos. Cualquier cambio aquí es un cambio de contrato entre servicios.</p>
 *
 * <p>{@code values} llega como un mapa abierto a propósito: el emisor publica datos maestros de
 * entidades muy distintas (estaciones, vías, ménsulas) con un único tipo de mensaje. Traducir ese
 * mapa a tipos de este dominio es trabajo del manejador, no del transporte.</p>
 *
 * <p>Ejemplo, el {@code data} de un perfil modificado tal como lo lee {@code ProfileMasterDataHandler}:</p>
 * <pre>{@code
 * {"entityName": "profile", "entityId": "4321", "operation": "UPDATED",
 *  "values": {"id": 4321, "profileId": "13-2.01", "kp": 13007.290, "track": {"id": 2, "executionPackageId": 6, …}, …}}
 * }</pre>
 *
 * @param entityName nombre lógico de la entidad, normalizado en kebab-case (p. ej. {@code station})
 * @param entityId   identificador de la entidad en el sistema de origen, siempre como texto
 * @param operation  alta, modificación o baja
 * @param values     atributos publicados de la entidad; puede venir vacío
 */
public record MasterDataChangedEvent(
        String entityName,
        String entityId,
        MasterDataOperation operation,
        Map<String, Object> values
) {
}
