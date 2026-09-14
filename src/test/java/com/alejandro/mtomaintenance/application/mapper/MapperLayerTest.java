package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderResponse;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftResponse;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionTemplateItem;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskCheckItem;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;
import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectResponse;
import com.alejandro.mtomaintenance.application.dto.history.StatusHistoryResponse;
import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateResponse;
import com.alejandro.mtomaintenance.application.dto.inspection.MaintenanceInspectionResponse;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageResponse;
import com.alejandro.mtomaintenance.application.dto.tasktype.MaintenanceTaskTypeResponse;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamSummaryResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryDefect;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.FunctionalGroup;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionResult;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionTemplate;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspection;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspectionItem;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceStatusHistory;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTeam;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockSyncStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TaskUnit;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los mappers generados por MapStruct se cargan como beans: es la unica forma de resolver sus dependencias sin adivinar el orden del constructor. */
class MapperLayerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AuditableMapperImpl.class, CatenaryAssetMapperImpl.class, MaintenanceTeamMapperImpl.class,
                    ChecklistMapperImpl.class, MaintenanceOrderMapperImpl.class, MaintenanceTaskMapperImpl.class,
                    MaintenanceShiftMapperImpl.class, CatenaryDefectMapperImpl.class, MaintenanceInspectionMapperImpl.class,
                    MaterialUsageMapperImpl.class, StatusHistoryMapperImpl.class, InspectionTemplateMapperImpl.class,
                    MaintenanceTaskTypeMapperImpl.class);

    @Test
    void orderMapperCombinesEntityAssetTeamAndComputedFields() {
        contextRunner.run(context -> {
            MaintenanceOrderMapper mapper = context.getBean(MaintenanceOrderMapper.class);
            CatenaryAsset asset = asset();
            MaintenanceOrder order = MaintenanceOrder.builder().code("MO-000001").title("Preventive T2").type(MaintenanceOrderType.PREVENTIVE)
                    .asset(asset).plannedDate(LocalDate.of(2026, 1, 27)).build();
            order.locateAt(asset);
            ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(order, "createdAt", Instant.EPOCH);
            ReflectionTestUtils.setField(order, "createdBy", "alejandro");

            MaintenanceOrderResponse response = mapper.toResponse(order, 23, 5, new BigDecimal("1266.00"), 5);

            assertEquals("MO-000001", response.code());
            assertEquals("SEC-T2", response.asset().code());
            assertEquals(2L, response.trackId());
            assertEquals(23, response.taskCount());
            assertEquals(5, response.completedTaskCount());
            assertEquals(new BigDecimal("1266.00"), response.estimatedMinutes());
            assertEquals("alejandro", response.audit().createdBy());
            assertNull(response.team());
            assertNull(response.originInspectionId());
        });
    }

    @Test
    void taskMapperOrdersTaskTypeCodesByCatalogueOrderAndFlagsOutOfRangeItems() {
        contextRunner.run(context -> {
            MaintenanceTaskMapper mapper = context.getBean(MaintenanceTaskMapper.class);
            MaintenanceTask task = MaintenanceTask.builder().sequence(3).description("Profile 12-2.27").order(MaintenanceOrder.builder().code("MO-1").build()).build();
            task.getTaskTypes().add(MaintenanceTaskType.builder().code("RG-12").description("Droppers").orderIndex(12).build());
            task.getTaskTypes().add(MaintenanceTaskType.builder().code("RG-01").description("Insulators").orderIndex(1).build());
            MaintenanceTaskCheckItem item = MaintenanceTaskCheckItem.fromTemplate(task, InspectionTemplateItem.builder()
                    .code("CW_HEIGHT").label("Height").unit("mm").minValue(new BigDecimal("5000")).maxValue(new BigDecimal("5500")).requiresMeasure(true).build());
            item.setMeasuredValue(new BigDecimal("5700"));
            task.getCheckItems().add(item);

            MaintenanceTaskResponse response = mapper.toResponse(task);

            assertEquals(List.of("RG-01", "RG-12"), response.taskTypeCodes());
            assertEquals(1, response.checkItems().size());
            assertTrue(response.checkItems().getFirst().outOfRange());
            assertNull(response.shiftId());
        });
    }

    @Test
    void shiftMapperExposesTheDisconnectorsOpenedAtBothEnds() {
        contextRunner.run(context -> {
            MaintenanceShiftMapper mapper = context.getBean(MaintenanceShiftMapper.class);
            CatenaryAsset blockA = CatenaryAsset.builder().code("DSC-1").name("HSA-NS5").type(CatenaryAssetType.DISCONNECTOR).build();
            MaintenanceShift shift = MaintenanceShift.builder().code("SH-000001").shiftDate(LocalDate.of(2026, 1, 27))
                    .possessionType(PossessionType.PARTIAL).trackIds(new java.util.LinkedHashSet<>(java.util.List.of(2L, 1L))).blockADisconnector(blockA).build();

            MaintenanceShiftResponse response = mapper.toResponse(shift);

            assertEquals(java.util.List.of(1L, 2L), response.trackIds(), "Las vias salen ordenadas");

            assertEquals("DSC-1", response.blockADisconnectorCode());
            assertNull(response.blockBDisconnectorCode());
            assertNotNull(response.status());
        });
    }

    private static CatenaryAsset asset() {
        return CatenaryAsset.builder().code("SEC-T2").name("Ranana-Herzliya T2").type(CatenaryAssetType.TRACK_SECTION)
                .trackId(2L).executionPackageId(6L).startKp(new BigDecimal("12847.990")).endKp(new BigDecimal("14078.090"))
                .trackKind(TrackKind.MAIN).build();
    }

    @Test
    void defectAndInspectionMappersFlattenTheirAssociationsToIds() {
        contextRunner.run(context -> {
            CatenaryAsset asset = asset();
            ReflectionTestUtils.setField(asset, "id", UUID.randomUUID());
            MaintenanceOrder order = MaintenanceOrder.builder().code("MO-1").title("Corrective").type(MaintenanceOrderType.CORRECTIVE).asset(asset).build();
            ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
            MaintenanceShift shift = MaintenanceShift.builder().code("SH-1").shiftDate(LocalDate.of(2026, 1, 27)).possessionType(PossessionType.PARTIAL).build();
            ReflectionTestUtils.setField(shift, "id", UUID.randomUUID());
            MaintenanceTask task = MaintenanceTask.builder().order(order).sequence(1).description("Profile").build();
            ReflectionTestUtils.setField(task, "id", UUID.randomUUID());
            InspectionTemplate template = InspectionTemplate.builder().assetType(CatenaryAssetType.TRACK_SECTION).name("Section").build();
            ReflectionTestUtils.setField(template, "id", UUID.randomUUID());
            MaintenanceInspection inspection = MaintenanceInspection.builder().code("INS-1").asset(asset).inspectionDate(LocalDate.of(2026, 1, 28))
                    .result(InspectionResult.MAJOR_DEFECT).template(template).originOrder(order).shift(shift).build();
            ReflectionTestUtils.setField(inspection, "id", UUID.randomUUID());
            MaintenanceInspectionItem item = MaintenanceInspectionItem.fromTemplate(inspection, InspectionTemplateItem.builder()
                    .code("CW_HEIGHT").label("Height").unit("mm").minValue(new BigDecimal("5000")).maxValue(new BigDecimal("5500")).requiresMeasure(true).build());
            item.setMeasuredValue(new BigDecimal("4900"));
            inspection.getItems().add(item);
            CatenaryDefect defect = CatenaryDefect.builder().code("DEF-1").asset(asset).inspection(inspection).order(order).severity(DefectSeverity.HIGH)
                    .status(DefectStatus.RESOLVED).description("kink").detectedAt(Instant.EPOCH).resolvedInShift(shift).foundInTask(task)
                    .photoRefs(new java.util.ArrayList<>(List.of("kink.jpg"))).build();
            inspection.setGeneratedDefect(defect);
            ReflectionTestUtils.setField(defect, "id", UUID.randomUUID());

            CatenaryDefectResponse defectResponse = context.getBean(CatenaryDefectMapper.class).toResponse(defect);
            MaintenanceInspectionResponse inspectionResponse = context.getBean(MaintenanceInspectionMapper.class).toResponse(inspection);

            assertEquals(defect.getId(), defectResponse.id());
            assertEquals("SEC-T2", defectResponse.asset().code());
            assertEquals(inspection.getId(), defectResponse.inspectionId());
            assertEquals(order.getId(), defectResponse.orderId());
            assertEquals(shift.getId(), defectResponse.resolvedInShiftId());
            assertEquals(task.getId(), defectResponse.foundInTaskId());
            assertEquals(List.of("kink.jpg"), defectResponse.photoRefs());
            assertEquals(DefectStatus.RESOLVED, defectResponse.status());

            assertEquals(template.getId(), inspectionResponse.templateId());
            assertEquals(defect.getId(), inspectionResponse.generatedDefectId());
            assertNull(inspectionResponse.generatedOrderId());
            assertEquals(order.getId(), inspectionResponse.originOrderId());
            assertEquals(shift.getId(), inspectionResponse.shiftId());
            assertEquals(1, inspectionResponse.items().size());
            assertEquals("CW_HEIGHT", inspectionResponse.items().getFirst().code());
            assertTrue(inspectionResponse.items().getFirst().outOfRange(), "4900 mm is below the 5000 mm minimum");
        });
    }

    @Test
    void materialHistoryTemplateTaskTypeAndTeamMappersCopyThePlainFieldsAndIds() {
        contextRunner.run(context -> {
            CatenaryAsset asset = asset();
            MaintenanceOrder order = MaintenanceOrder.builder().code("MO-1").title("Order").type(MaintenanceOrderType.PREVENTIVE).asset(asset).build();
            ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
            MaintenanceTask task = MaintenanceTask.builder().order(order).sequence(1).description("Profile").build();
            ReflectionTestUtils.setField(task, "id", UUID.randomUUID());
            MaintenanceMaterialUsage usage = MaintenanceMaterialUsage.builder().order(order).task(task).materialId(UUID.randomUUID()).materialCode("GA70")
                    .warehouseId(UUID.randomUUID()).plannedQuantity(new BigDecimal("2")).unit("ud").build();
            usage.markFailed("reserve: stock down");
            MaintenanceStatusHistory history = MaintenanceStatusHistory.builder().order(order).previousStatus("DRAFT").newStatus("PLANNED")
                    .changedBy("alejandro").comment("week 4").build();
            InspectionTemplate template = InspectionTemplate.builder().assetType(CatenaryAssetType.PROFILE).name("Profile").version(2).build();
            template.getItems().add(InspectionTemplateItem.builder().template(template).code("CW_HEIGHT").label("Height").unit("mm").orderIndex(1).build());
            template.getItems().add(InspectionTemplateItem.builder().template(template).code("DROPPERS").label("Droppers").orderIndex(8).build());
            MaintenanceTaskType messenger = MaintenanceTaskType.builder().code("RG-08").description("Messenger wire").functionalGroup(FunctionalGroup.OVERHEAD_CONDUCTORS)
                    .unit(TaskUnit.KM).fixedMinutes(new BigDecimal("60")).standardMinutesPerUnit(new BigDecimal("3")).orderIndex(8).build();
            MaintenanceTeam team = MaintenanceTeam.builder().code("A").name("Team A").baseName("Rishpon").vehicle("Vehicle A")
                    .executionPackageIds(new LinkedHashSet<>(Set.of(6L, 7L))).build();
            ReflectionTestUtils.setField(team, "id", UUID.randomUUID());

            MaterialUsageResponse usageResponse = context.getBean(MaterialUsageMapper.class).toResponse(usage);
            StatusHistoryResponse historyResponse = context.getBean(StatusHistoryMapper.class).toResponse(history);
            InspectionTemplateResponse templateResponse = context.getBean(InspectionTemplateMapper.class).toResponse(template);
            MaintenanceTaskTypeResponse typeResponse = context.getBean(MaintenanceTaskTypeMapper.class).toResponse(messenger);
            MaintenanceTeamSummaryResponse teamSummary = context.getBean(MaintenanceTeamMapper.class).toSummary(team);

            assertEquals(order.getId(), usageResponse.orderId());
            assertEquals(task.getId(), usageResponse.taskId());
            assertEquals(StockSyncStatus.FAILED, usageResponse.stockSyncStatus());
            assertEquals("reserve: stock down", usageResponse.stockSyncError());
            assertEquals(0, BigDecimal.ZERO.compareTo(usageResponse.consumedQuantity()));
            assertEquals("DRAFT", historyResponse.previousStatus());
            assertEquals("PLANNED", historyResponse.newStatus());
            assertEquals("alejandro", historyResponse.changedBy());
            assertNotNull(historyResponse.changedAt());
            assertEquals(2, templateResponse.version());
            assertEquals(List.of("CW_HEIGHT", "DROPPERS"), templateResponse.items().stream().map(item -> item.code()).toList());
            assertEquals(TaskUnit.KM, typeResponse.unit());
            assertEquals(new BigDecimal("60"), typeResponse.fixedMinutes());
            assertEquals(FunctionalGroup.OVERHEAD_CONDUCTORS, typeResponse.functionalGroup());
            assertEquals(team.getId(), teamSummary.id());
            assertEquals("Rishpon", teamSummary.baseName());
            assertEquals(Set.of(6L, 7L), context.getBean(MaintenanceTeamMapper.class).toResponse(team).executionPackageIds());
        });
    }
}
