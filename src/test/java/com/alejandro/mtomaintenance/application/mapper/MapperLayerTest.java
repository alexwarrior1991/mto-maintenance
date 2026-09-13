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
                    MaintenanceShiftMapperImpl.class);

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
                    .possessionType(PossessionType.PARTIAL).trackId(2L).blockADisconnector(blockA).build();

            MaintenanceShiftResponse response = mapper.toResponse(shift);

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
}
