package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Referencias ligeras (solo id) para rellenar relaciones sin cargar la entidad. Las usan MapStruct y
 * los servicios cuando el id ya esta validado.
 */
@Component
public class EntityReferenceFactory {

    public CatenaryAsset toAsset(UUID id) {
        if (id == null) {
            return null;
        }
        CatenaryAsset asset = CatenaryAsset.builder().build();
        asset.setId(id);
        return asset;
    }

    public MaintenanceOrder toOrder(UUID id) {
        if (id == null) {
            return null;
        }
        MaintenanceOrder order = MaintenanceOrder.builder().build();
        order.setId(id);
        return order;
    }

    public MaintenanceTeam toTeam(UUID id) {
        if (id == null) {
            return null;
        }
        MaintenanceTeam team = MaintenanceTeam.builder().build();
        team.setId(id);
        return team;
    }

    public MaintenanceShift toShift(UUID id) {
        if (id == null) {
            return null;
        }
        MaintenanceShift shift = MaintenanceShift.builder().build();
        shift.setId(id);
        return shift;
    }
}
