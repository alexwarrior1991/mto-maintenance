package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataChangedMessage;
import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataEntityNames;
import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataEventContext;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import com.alejandro.mtomaintenance.application.service.MasterDataEntityHandler;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Via: solo interesa su borrado, que deja sin sentido todo lo que hay sobre ella (perfiles y tramos
 * incluidos). Las altas y cambios no tocan nada: el activo no guarda snapshot de la via, y atender
 * el UPDATED obligaria a una segunda marca de agua de otro agregado sobre la misma fila.
 */
@Service
@RequiredArgsConstructor
class TrackMasterDataHandler implements MasterDataEntityHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrackMasterDataHandler.class);

    private final CatenaryAssetRepository repository;

    @Override
    public String entityName() {
        return MasterDataEntityNames.TRACK;
    }

    @Override
    public void onDeleted(MasterDataChangedMessage message, MasterDataEventContext context) {
        String entityId = MasterDataPayload.sourceEntityId(message.data(), entityName());
        long trackId;
        try {
            trackId = Long.parseLong(entityId);
        } catch (NumberFormatException exception) {
            throw new ValidationException("Track entityId '" + entityId + "' is not numeric");
        }
        int disabled = repository.deactivateByTrack(trackId);
        LOGGER.info("Track deleted: trackId={}, assets disabled={}, sequenceNumber={}", trackId, disabled, context.sequenceNumber());
    }
}
