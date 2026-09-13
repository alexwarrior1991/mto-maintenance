package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.envers.Audited;
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
                CatenaryDefect.class, MaintenanceMaterialUsage.class);
        List<Class<?>> notAudited = List.of(MaintenanceStatusHistory.class, InboxMessage.class, MaintenanceTeam.class,
                MaintenanceTaskType.class, InspectionTemplate.class, InspectionTemplateItem.class, AuditableEntity.class);

        audited.forEach(type -> assertTrue(type.isAnnotationPresent(Audited.class), type.getSimpleName() + " must be @Audited"));
        notAudited.forEach(type -> assertFalse(type.isAnnotationPresent(Audited.class), type.getSimpleName() + " must not be @Audited"));
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
