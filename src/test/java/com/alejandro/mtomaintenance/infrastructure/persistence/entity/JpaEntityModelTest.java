package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpaEntityModelTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * La particion auditada / no auditada es una decision de diseno (ver V4): un @Audited de mas en
     * una entidad sin gemela _aud impide arrancar con ddl-auto: validate, y uno de menos deja sin
     * historial algo que lo necesita.
     */
    @Test
    void enversAuditsExactlyTheAgreedEntities() {
        List<Class<?>> audited = List.of(CatenaryAsset.class, MaintenanceShift.class, MaintenanceOrder.class, MaintenanceTask.class,
                MaintenanceTaskCheckItem.class, MaintenanceInspection.class, MaintenanceInspectionItem.class,
                CatenaryDefect.class, MaintenanceMaterialUsage.class, MaintenanceShiftVehicle.class);
        // CrewPerson, FleetVehicle y MaintenanceInstrument (V23) son instantaneas de mto-resources escritas
        // solo con SQL nativo: como CatenaryAssetSwitch, una gemela _aud se quedaria vacia. Su historial vive
        // en mto-resources.
        // CatenaryAssetSwitch se queda fuera por lo mismo que InboxMessage: sus filas las escribe
        // UNICAMENTE el manejador de datos maestros, asi que una gemela _aud se quedaria vacia y se
        // leeria como "nunca cambio". El historial del aislador vive en mto-configuration.
        // OutboxMessage tampoco: la escribe solo el relay y su historia es ella misma (V13). Ni
        // BackgroundOperation (V21): la escriben sus sentencias nativas y es su propio historico.
        List<Class<?>> notAudited = List.of(MaintenanceStatusHistory.class, InboxMessage.class, MaintenanceTeam.class,
                MaintenanceTaskType.class, InspectionTemplate.class, InspectionTemplateItem.class,
                CatenaryAssetSwitch.class, CrewPerson.class, FleetVehicle.class, MaintenanceInstrument.class, BackgroundOperation.class,
                AuditableEntity.class,
                com.alejandro.mtomaintenance.infrastructure.messaging.outbox.OutboxMessage.class);

        audited.forEach(type -> assertTrue(type.isAnnotationPresent(Audited.class), type.getSimpleName() + " must be @Audited"));
        notAudited.forEach(type -> assertFalse(type.isAnnotationPresent(Audited.class), type.getSimpleName() + " must not be @Audited"));
    }

    /**
     * Lo que un turno toma de mto-resources (la dotacion, los vehiculos y, desde V23, los instrumentos) no
     * tiene gemela _aud: la revision del turno lo lee como esta ahora. Sin NOT_AUDITED, Envers buscaria
     * maintenance_instrument_aud, que no existe.
     */
    @Test
    void theSnapshotsOfMtoResourcesHangingFromAShiftAreReadAsTheyAreNow() throws NoSuchFieldException {
        for (String field : List.of("teamLeader", "supervisor", "linemen", "instruments")) {
            Audited audited = MaintenanceShift.class.getDeclaredField(field).getAnnotation(Audited.class);
            assertTrue(audited != null && audited.targetAuditMode() == RelationTargetAuditMode.NOT_AUDITED, "MaintenanceShift." + field);
        }
        Audited vehicle = MaintenanceShiftVehicle.class.getDeclaredField("vehicle").getAnnotation(Audited.class);
        assertTrue(vehicle != null && vehicle.targetAuditMode() == RelationTargetAuditMode.NOT_AUDITED, "MaintenanceShiftVehicle.vehicle");
    }

    @Test
    void assetValidationRejectsBlankCodeAndNonPositiveInterval() {
        CatenaryAsset asset = CatenaryAsset.builder().code(" ").name("Profile").type(CatenaryAssetType.PROFILE).preventiveIntervalDays(0).build();

        var violations = validator.validate(asset);

        assertTrue(violations.stream().anyMatch(violation -> "code".contentEquals(violation.getPropertyPath().toString())));
        assertTrue(violations.stream().anyMatch(violation -> "preventiveIntervalDays".contentEquals(violation.getPropertyPath().toString())));
        assertTrue(asset.isEnabled());
        assertFalse(asset.isFromMasterData());
    }

    @Test
    void checklistItemsReportOutOfRangeMeasurementsAndPreferTheAdjustedValue() {
        InspectionTemplateItem template = InspectionTemplateItem.builder()
                .code("CW_HEIGHT").label("Height").unit("mm").minValue(new BigDecimal("5000")).maxValue(new BigDecimal("5500")).requiresMeasure(true).build();
        MaintenanceTask task = MaintenanceTask.builder().sequence(1).description("Profile").build();
        MaintenanceTaskCheckItem item = MaintenanceTaskCheckItem.fromTemplate(task, template);

        assertEquals("CW_HEIGHT", item.getCode());
        assertFalse(item.isOutOfRange());
        item.setMeasuredValue(new BigDecimal("5620"));
        assertTrue(item.isOutOfRange());
        item.setValueAfterAdjustment(new BigDecimal("5300"));
        assertFalse(item.isOutOfRange());
        assertEquals(new BigDecimal("5300"), item.effectiveValue());
    }

    @Test
    void photoReferencesRoundTripThroughOneLinePerReference() {
        PhotoReferencesConverter converter = new PhotoReferencesConverter();

        assertEquals("a.jpg\nb.jpg", converter.convertToDatabaseColumn(List.of("a.jpg", "b.jpg")));
        assertEquals(List.of("a.jpg", "b.jpg"), converter.convertToEntityAttribute("a.jpg\n\nb.jpg\n"));
        assertEquals(List.of(), converter.convertToEntityAttribute(null));
        assertEquals(null, converter.convertToDatabaseColumn(List.of()));
    }

    @Test
    void materialUsageTracksTheStockConversation() {
        MaintenanceMaterialUsage usage = MaintenanceMaterialUsage.builder()
                .materialId(java.util.UUID.randomUUID()).warehouseId(java.util.UUID.randomUUID())
                .plannedQuantity(BigDecimal.ONE).unit("ud").build();
        java.util.UUID reservation = java.util.UUID.randomUUID();

        assertEquals(StockSyncStatus.NOT_REQUESTED, usage.getStockSyncStatus());
        usage.markFailed("boom");
        assertTrue(usage.isSyncFailed());
        usage.markReserved(reservation);
        assertEquals(StockSyncStatus.RESERVED, usage.getStockSyncStatus());
        assertEquals(reservation, usage.getStockReservationId());
        assertEquals(null, usage.getStockSyncError());
    }
}
