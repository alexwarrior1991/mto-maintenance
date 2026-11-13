package com.alejandro.mtomaintenance.infrastructure.web.controller;

import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateItemResponse;
import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateResponse;
import com.alejandro.mtomaintenance.application.service.InspectionTemplateService;
import com.alejandro.mtomaintenance.application.dto.operation.OperationResponse;
import com.alejandro.mtomaintenance.application.dto.operation.OperationStatus;
import com.alejandro.mtomaintenance.application.dto.order.CancelOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.CompleteOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderResponse;
import com.alejandro.mtomaintenance.application.exception.AssetDisabledException;
import com.alejandro.mtomaintenance.application.exception.InvalidTransitionException;
import com.alejandro.mtomaintenance.application.exception.MaterialUsageException;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.service.BudgetedOperations;
import com.alejandro.mtomaintenance.application.service.CatenaryAssetService;
import com.alejandro.mtomaintenance.application.service.MaintenanceMaterialUsageService;
import com.alejandro.mtomaintenance.application.service.MaintenanceOrderService;
import com.alejandro.mtomaintenance.application.service.MaintenanceTaskService;
import com.alejandro.mtomaintenance.application.service.StatusHistoryService;
import com.alejandro.mtomaintenance.configuration.security.RestAccessDeniedHandler;
import com.alejandro.mtomaintenance.configuration.security.RestAuthenticationEntryPoint;
import com.alejandro.mtomaintenance.configuration.security.SecurityAuthorityPrefixes;
import com.alejandro.mtomaintenance.configuration.security.SecurityConfiguration;
import com.alejandro.mtomaintenance.configuration.security.SecurityRoles;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import com.alejandro.mtomaintenance.infrastructure.web.exception.GlobalExceptionHandler;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageResponse;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksRequest;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksResponse;
import com.alejandro.mtomaintenance.application.dto.task.GenerationOutcome;
import com.alejandro.mtomaintenance.application.dto.task.GenerationPreviewLine;
import com.alejandro.mtomaintenance.application.dto.task.GenerationSelection;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskResponse;
import com.alejandro.mtomaintenance.application.exception.ShiftException;
import com.alejandro.mtomaintenance.application.exception.StockRejectedException;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockSyncStatus;
import java.util.List;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import com.alejandro.mtomaintenance.application.dto.common.MergePatch;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderUpdateRequest;
import com.alejandro.mtomaintenance.application.exception.StaleVersionException;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.core.TypeInformation;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Set;

import static org.mockito.ArgumentMatchers.argThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP del controlador de ordenes con la cadena de seguridad real: aqui se prueba que el
 * rol de supervision protege 'cancel' y el 'force' de 'complete' (metodo), ademas del 201, el 400 y
 * los codigos de error estables. Carga tambien el de activos, por la otra regla de metodo: desactivar
 * con un PUT pide el rol de borrado, como el DELETE.
 */
@WebMvcTest({MaintenanceOrderController.class, CatenaryAssetController.class, InspectionTemplateController.class})
@Import({SecurityConfiguration.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class MaintenanceOrderControllerMockMvcTest {

    private static final String ORDERS = "/api/v1/maintenance/orders";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MaintenanceOrderService orderService;

    @MockitoBean
    private MaintenanceTaskService taskService;

    @MockitoBean
    private MaintenanceMaterialUsageService materialService;

    @MockitoBean
    private StatusHistoryService historyService;

    @MockitoBean
    private CatenaryAssetService assetService;

    @MockitoBean
    private BudgetedOperations operations;

    @MockitoBean
    private InspectionTemplateService templateService;

    @Test
    void disablingAnAssetWithAPutNeedsTheDeleteRoleLikeTheDeleteAndTheRestOfThePutDoesNot() throws Exception {
        UUID assetId = UUID.randomUUID();
        String assets = "/api/v1/maintenance/assets/{id}";

        mockMvc.perform(put(assets, assetId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
        verify(assetService, never()).update(any(), any());

        mockMvc.perform(put(assets, assetId).with(role(SecurityRoles.MAINTENANCE_WRITE, SecurityRoles.MAINTENANCE_DELETE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(put(assets, assetId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"description\":\"Next to the bridge\"}"))
                .andExpect(status().isOk());
        verify(assetService, times(2)).update(eq(assetId), any());

        when(assetService.update(eq(assetId), any())).thenThrow(new AssetDisabledException("Catenary asset PRF-1 is disabled in mto-configuration"));
        mockMvc.perform(put(assets, assetId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AST-001"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aMergePatchEmptiesWhatComesAsNullAndIsValidatedLikeAPut() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.patch(eq(orderId), any())).thenReturn(response(orderId));

        mockMvc.perform(patch(ORDERS + "/{id}", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MergePatch.MEDIA_TYPE)
                        .content("{\"plannedDate\":null,\"teamId\":null,\"description\":\"Night work\",\"version\":3}"))
                .andExpect(status().isOk());

        ArgumentCaptor<MergePatch<MaintenanceOrderUpdateRequest>> captor = ArgumentCaptor.forClass(MergePatch.class);
        verify(orderService).patch(eq(orderId), captor.capture());
        assertEquals(Set.of("plannedDate", "teamId"), captor.getValue().cleared());
        assertEquals("Night work", captor.getValue().values().description());
        assertEquals(3L, captor.getValue().values().version());

        // version a null es no mandar version, no vaciarla.
        mockMvc.perform(patch(ORDERS + "/{id}", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MergePatch.MEDIA_TYPE).content("{\"version\":null}"))
                .andExpect(status().isOk());
        verify(orderService, times(2)).patch(eq(orderId), captor.capture());
        assertTrue(captor.getValue().cleared().isEmpty());

        // Un null con el nombre mal escrito no vaciaria nada sin decirlo: 400.
        mockMvc.perform(patch(ORDERS + "/{id}", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MergePatch.MEDIA_TYPE).content("{\"plannedDat\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VAL-001"));
        mockMvc.perform(patch(ORDERS + "/{id}", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MergePatch.MEDIA_TYPE).content("{\"title\":\"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQ-VALIDATION"))
                .andExpect(jsonPath("$.validationErrors[0].field").value("title"));
        mockMvc.perform(patch(ORDERS + "/{id}", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MergePatch.MEDIA_TYPE).content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQ-400"));
        verify(orderService, times(2)).patch(eq(orderId), any());

        when(orderService.patch(eq(orderId), any())).thenThrow(new StaleVersionException("Order MO-1 was changed by someone else"));
        mockMvc.perform(patch(ORDERS + "/{id}", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MergePatch.MEDIA_TYPE).content("{\"version\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CON-001"));
    }

    @Test
    void theNestedResourcesTakeAMergePatchTooAndDisablingAnAssetByPatchNeedsTheDeleteRole() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID usageId = UUID.randomUUID();

        mockMvc.perform(patch(ORDERS + "/{id}/tasks/{taskId}", orderId, taskId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MergePatch.MEDIA_TYPE).content("{\"notes\":null}"))
                .andExpect(status().isOk());
        verify(taskService).patch(eq(orderId), eq(taskId), argThat(patch -> patch.clears("notes")));
        mockMvc.perform(patch(ORDERS + "/{id}/tasks/{taskId}/check-items/{itemId}", orderId, taskId, itemId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MergePatch.MEDIA_TYPE).content("{\"measuredValue\":null,\"version\":0}"))
                .andExpect(status().isOk());
        verify(taskService).patchCheckItem(eq(orderId), eq(taskId), eq(itemId), argThat(patch -> patch.clears("measuredValue")));
        mockMvc.perform(patch(ORDERS + "/{id}/materials/{usageId}", orderId, usageId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MergePatch.MEDIA_TYPE).content("{\"consumedQuantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQ-VALIDATION"));
        verify(materialService, never()).patch(any(), any(), any());

        UUID assetId = UUID.randomUUID();
        mockMvc.perform(patch("/api/v1/maintenance/assets/{id}", assetId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MergePatch.MEDIA_TYPE).content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/maintenance/assets/{id}", assetId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MergePatch.MEDIA_TYPE).content("{\"preventiveIntervalDays\":null}"))
                .andExpect(status().isOk());
        verify(assetService).patch(eq(assetId), argThat(patch -> patch.clears("preventiveIntervalDays")));
    }

    /**
     * Una cantidad es {@code numeric(19,6)}: 19 cifras, y un {@code double} guarda 15. El merge-patch se lee
     * primero como arbol, y un arbol con los decimales como {@code double} entregaba otra cantidad.
     */
    @Test
    void aQuantityInAMergePatchArrivesDigitByDigit() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID usageId = UUID.randomUUID();
        BigDecimal exact = new BigDecimal("1234567890123.123456");

        mockMvc.perform(patch(ORDERS + "/{id}/materials/{usageId}", orderId, usageId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MergePatch.MEDIA_TYPE).content("{\"plannedQuantity\":1234567890123.123456,\"version\":1}"))
                .andExpect(status().isOk());
        verify(materialService).patch(eq(orderId), eq(usageId),
                argThat(patch -> patch.values().plannedQuantity() != null && exact.compareTo(patch.values().plannedQuantity()) == 0));

        mockMvc.perform(patch(ORDERS + "/{id}/materials/{usageId}", orderId, usageId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MergePatch.MEDIA_TYPE).content("{\"consumedQuantity\":\"1234567890123.123456\",\"version\":1}"))
                .andExpect(status().isOk());
        verify(materialService).patch(eq(orderId), eq(usageId),
                argThat(patch -> patch.values().consumedQuantity() != null && exact.compareTo(patch.values().consumedQuantity()) == 0));
    }

    @Test
    void createReturnsCreatedLocationAndJsonBody() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(orderService.create(any(MaintenanceOrderRequest.class))).thenReturn(response(orderId));

        mockMvc.perform(post(ORDERS).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Preventive T2\",\"type\":\"PREVENTIVE\",\"assetId\":\"" + assetId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", ORDERS + "/" + orderId))
                .andExpect(jsonPath("$.code").value("MO-000001"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void createReturnsValidationErrorsForAMissingTitleAndAsset() throws Exception {
        mockMvc.perform(post(ORDERS).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \",\"type\":\"PREVENTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQ-VALIDATION"))
                .andExpect(jsonPath("$.validationErrors", hasSize(2)));
    }

    @Test
    void cancellingNeedsTheSuperviseRoleOnTopOfWrite() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.cancel(eq(orderId), any())).thenReturn(response(orderId));

        mockMvc.perform(post(ORDERS + "/{id}/cancel", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"rain\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH-403"));
        verify(orderService, never()).cancel(any(), any());

        mockMvc.perform(post(ORDERS + "/{id}/cancel", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE, SecurityRoles.MAINTENANCE_SUPERVISE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"rain\"}"))
                .andExpect(status().isOk());
        verify(orderService).cancel(orderId, new CancelOrderRequest("rain"));
    }

    @Test
    void forcedCompletionNeedsTheSuperviseRoleButAPlainCompletionDoesNot() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.complete(eq(orderId), any())).thenReturn(response(orderId));

        mockMvc.perform(post(ORDERS + "/{id}/complete", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"closingNotes\":\"done\",\"force\":true}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(ORDERS + "/{id}/complete", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"closingNotes\":\"done\"}"))
                .andExpect(status().isOk());
        verify(orderService).complete(orderId, new CompleteOrderRequest("done", null, null));
    }

    @Test
    void serviceExceptionsAreRenderedAsStableErrorResponses() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.findById(orderId)).thenThrow(new NotFoundException("Maintenance order", orderId));
        when(orderService.start(eq(orderId), any())).thenThrow(new InvalidTransitionException("Order MO-1 is CANCELLED and cannot be started"));

        mockMvc.perform(get(ORDERS + "/{id}", orderId).with(role(SecurityRoles.MAINTENANCE_READ)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ORD-404"));
        mockMvc.perform(post(ORDERS + "/{id}/start", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TRN-001"));
        mockMvc.perform(get(ORDERS + "/{id}", orderId))
                .andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor role(String... roles) {
        String[] authorities = new String[roles.length];
        for (int index = 0; index < roles.length; index++) {
            authorities[index] = SecurityAuthorityPrefixes.ROLE_PREFIX + roles[index];
        }
        return jwt().authorities(AuthorityUtils.createAuthorityList(authorities));
    }

    private static MaintenanceOrderResponse response(UUID id) {
        return new MaintenanceOrderResponse(id, "MO-000001", "Preventive T2", null, MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT,
                MaintenancePriority.MEDIUM, null, 6L, 2L, null, null, null, null, null, null, null, null, null, null, null, null, null,
                0, 0, BigDecimal.ZERO, 0, null, null);
    }

    @Test
    void anUnknownSortAndAnUnsupportedMethodAreClientErrors() throws Exception {
        when(orderService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new PropertyReferenceException("nope", TypeInformation.of(MaintenanceOrder.class), List.of()));

        mockMvc.perform(get(ORDERS).param("sort", "nope,asc").with(role(SecurityRoles.MAINTENANCE_READ)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQ-400"))
                .andExpect(jsonPath("$.validationErrors[0].field").value("sort"));

        mockMvc.perform(delete(ORDERS + "/{id}", UUID.randomUUID()).with(role(SecurityRoles.MAINTENANCE_DELETE)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("REQ-405"))
                .andExpect(header().exists("Allow"));

        // El PATCH existe, pero solo como merge patch: con JSON a secas no se sabria que un null vacia.
        mockMvc.perform(patch(ORDERS + "/{id}", UUID.randomUUID()).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("REQ-415"));
    }

    @Test
    void removingAMaterialLineNeedsTheDeleteRole() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID usageId = UUID.randomUUID();

        mockMvc.perform(delete(ORDERS + "/{id}/materials/{usageId}", orderId, usageId).with(role(SecurityRoles.MAINTENANCE_WRITE)))
                .andExpect(status().isForbidden());
        verify(materialService, never()).remove(any(), any());

        mockMvc.perform(delete(ORDERS + "/{id}/materials/{usageId}", orderId, usageId).with(role(SecurityRoles.MAINTENANCE_DELETE)))
                .andExpect(status().isNoContent());
        verify(materialService).remove(orderId, usageId);

        doThrow(new MaterialUsageException("Material GA70 was already consumed in stock; the line cannot be removed"))
                .when(materialService).remove(orderId, usageId);
        mockMvc.perform(delete(ORDERS + "/{id}/materials/{usageId}", orderId, usageId).with(role(SecurityRoles.MAINTENANCE_DELETE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MAT-001"));
    }

    @Test
    void aStockRejectionOnAnExplicitSyncAnswers409WhenStockIsShortAnd422OtherwiseWithTheReasonOfStock() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID usageId = UUID.randomUUID();
        when(materialService.sync(orderId, usageId))
                .thenThrow(new StockRejectedException("mto-stock rejected 'reserve 5 of GA70' with 409 STK-001: Insufficient available stock",
                        409, "STK-001", null))
                .thenThrow(new StockRejectedException("mto-stock rejected 'reserve 5 of GA70' with 422 WH-001: Warehouse 'W1' is inactive",
                        422, "WH-001", null));

        mockMvc.perform(post(ORDERS + "/{id}/materials/{usageId}/sync", orderId, usageId).with(role(SecurityRoles.MAINTENANCE_WRITE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STK-001"))
                .andExpect(jsonPath("$.message").value(containsString("Insufficient available stock")));
        mockMvc.perform(post(ORDERS + "/{id}/materials/{usageId}/sync", orderId, usageId).with(role(SecurityRoles.MAINTENANCE_WRITE)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.errorCode").value("STK-422"))
                .andExpect(jsonPath("$.message").value(containsString("WH-001")));
    }

    @Test
    void aStockOutageOnAnExplicitSyncAnswers503AndMaterialRegistrationValidatesAndAnswersCreated() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID usageId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(materialService.sync(orderId, usageId)).thenThrow(new StockUnavailableException("stock down"));
        when(materialService.register(eq(orderId), any())).thenReturn(materialResponse(usageId, orderId, warehouseId));

        mockMvc.perform(post(ORDERS + "/{id}/materials/{usageId}/sync", orderId, usageId).with(role(SecurityRoles.MAINTENANCE_WRITE)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("STK-503"))
                .andExpect(jsonPath("$.message").value("stock down"));

        mockMvc.perform(post(ORDERS + "/{id}/materials", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialCode\":\"GA70\",\"warehouseId\":\"" + warehouseId + "\",\"plannedQuantity\":2,\"unit\":\"ud\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", ORDERS + "/" + orderId + "/materials/" + usageId))
                .andExpect(jsonPath("$.stockSyncStatus").value("NOT_REQUESTED"));

        mockMvc.perform(post(ORDERS + "/{id}/materials", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedQuantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQ-VALIDATION"))
                .andExpect(jsonPath("$.validationErrors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder("warehouseId", "plannedQuantity", "materialReference")));
    }

    @Test
    void taskEndpointsValidateTheirBodiesAndRenderShiftRulesAsConflicts() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID shiftId = UUID.randomUUID();
        when(taskService.create(eq(orderId), any())).thenReturn(taskResponse(taskId, orderId));
        when(taskService.complete(eq(orderId), eq(taskId), any())).thenThrow(new ShiftException("Shift SH-1 is PLANNED"));

        mockMvc.perform(post(ORDERS + "/{id}/tasks", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[0].field").value("description"));

        mockMvc.perform(post(ORDERS + "/{id}/tasks", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Profile 12-2.27\",\"taskTypeCodes\":[\"RG-01\"]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", ORDERS + "/" + orderId + "/tasks/" + taskId))
                .andExpect(jsonPath("$.sequence").value(1));

        mockMvc.perform(post(ORDERS + "/{id}/tasks/{taskId}/complete", orderId, taskId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shiftId\":\"" + shiftId + "\",\"inlineDefects\":[{\"severity\":\"LOW\",\"description\":\" \"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[0].field").value("inlineDefects[0].description"));

        mockMvc.perform(post(ORDERS + "/{id}/tasks/{taskId}/complete", orderId, taskId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"shiftId\":\"" + shiftId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SHF-001"));

        // Un elemento nulo en una lista es un 400 sobre ella, tambien al generar, que no validaba su
        // cuerpo: el codigo nulo era un NPE en el servicio, con un 500.
        mockMvc.perform(post(ORDERS + "/{id}/tasks/generate", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"taskTypeCodes\":[\"RG-01\",null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQ-VALIDATION"))
                .andExpect(jsonPath("$.validationErrors[0].field").value(org.hamcrest.Matchers.startsWith("taskTypeCodes")));
        mockMvc.perform(post(ORDERS + "/{id}/tasks/{taskId}/complete", orderId, taskId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"shiftId\":\"" + shiftId + "\",\"materials\":[null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[0].field").value(org.hamcrest.Matchers.startsWith("materials")));
        verify(taskService, never()).generatePreventiveTasks(any(), any());

        // Sin cuerpo genera con los tipos por defecto, como hasta ahora.
        when(taskService.generatePreventiveTasks(eq(orderId), any())).thenReturn(new GeneratePreventiveTasksResponse(3, 0, 3, java.math.BigDecimal.TEN, 1, 0,
                java.util.Map.of(), 0, java.util.List.of(), 0, 0, false, java.util.List.of()));
        mockMvc.perform(post(ORDERS + "/{id}/tasks/generate", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdTasks").value(3));
    }

    @Test
    void generatingWithSelectionsReadsEachOneAndAPreviewAnswersWithItsLines() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        GenerationPreviewLine line = new GenerationPreviewLine(assetId, "DSC-HSA-NS5", "HSA-NS5", CatenaryAssetType.DISCONNECTOR, 13L, null,
                new java.math.BigDecimal("12900.000"), java.util.List.of("RP-06"), false, GenerationOutcome.CREATE, null);
        when(taskService.generatePreventiveTasks(eq(orderId), any())).thenReturn(new GeneratePreventiveTasksResponse(1, 0, 4,
                java.math.BigDecimal.TEN, 1, 0, java.util.Map.of(CatenaryAssetType.DISCONNECTOR, 1), 1, java.util.List.of("MO-000042"), 0, 0,
                true, java.util.List.of(line)));

        mockMvc.perform(post(ORDERS + "/{id}/tasks/generate", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"selections": [
                                  {"assetType": "PROFILE", "sectionId": "%s"},
                                  {"assetType": "DISCONNECTOR", "trackId": 13, "fromKp": 12000.5, "toKp": 13000, "stationId": 4,
                                   "taskTypeCodes": ["RP-06"], "withChecklist": true}
                                ], "dryRun": true}""".formatted(sectionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.createdByAssetType.DISCONNECTOR").value(1))
                .andExpect(jsonPath("$.otherOrderCodes[0]").value("MO-000042"))
                .andExpect(jsonPath("$.assets[0].outcome").value("CREATE"))
                .andExpect(jsonPath("$.assets[0].taskTypeCodes[0]").value("RP-06"));

        ArgumentCaptor<GeneratePreventiveTasksRequest> request = ArgumentCaptor.forClass(GeneratePreventiveTasksRequest.class);
        verify(taskService).generatePreventiveTasks(eq(orderId), request.capture());
        assertTrue(request.getValue().previewOnly());
        assertEquals(new GenerationSelection(CatenaryAssetType.PROFILE, sectionId, null, null, null, null, null, null, null),
                request.getValue().selections().get(0));
        assertEquals(new GenerationSelection(CatenaryAssetType.DISCONNECTOR, null, 13L, new java.math.BigDecimal("12000.5"),
                new java.math.BigDecimal("13000"), 4L, null, java.util.List.of("RP-06"), true), request.getValue().selections().get(1));

        // Una seleccion sin tipo de activo es un 400 sobre ella, antes del servicio.
        mockMvc.perform(post(ORDERS + "/{id}/tasks/generate", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"selections\":[{\"trackId\":13}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[0].field").value("selections[0].assetType"));
        verify(taskService, times(1)).generatePreventiveTasks(any(), any());
    }

    @Test
    void aGenerationWithPreferRespondAsyncThatOutlastsItsBudgetAnswers202WithItsOperation() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        GeneratePreventiveTasksResponse generated = new GeneratePreventiveTasksResponse(3, 0, 3, java.math.BigDecimal.TEN, 1, 0,
                java.util.Map.of(), 0, java.util.List.of(), 0, 0, false, java.util.List.of());
        OperationResponse running = new OperationResponse(operationId, "GENERATE_TASKS", "EP-14", OperationStatus.RUNNING, "planner",
                java.time.Instant.parse("2026-10-16T21:00:00Z"), java.time.Instant.parse("2026-10-16T21:00:00Z"), null, null, null, null, null);
        when(taskService.generationScope(orderId)).thenReturn("EP-14");
        doReturn(new BudgetedOperations.Outcome<>(running, null, false))
                .when(operations).run(eq("GENERATE_TASKS"), eq("EP-14"), eq(200), eq(java.time.Duration.ofSeconds(2)), any());

        mockMvc.perform(post(ORDERS + "/{id}/tasks/generate", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .header("Prefer", "respond-async, wait=2"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", MaintenanceApiPaths.OPERATIONS + "/" + operationId))
                .andExpect(header().string("Preference-Applied", "respond-async"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.scope").value("EP-14"));
        verify(taskService, never()).generatePreventiveTasks(any(), any());

        // Si termina dentro del presupuesto, la respuesta es la de siempre.
        doReturn(new BudgetedOperations.Outcome<>(running, generated, true))
                .when(operations).run(eq("GENERATE_TASKS"), eq("EP-14"), eq(200), isNull(), any());
        mockMvc.perform(post(ORDERS + "/{id}/tasks/generate", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .header("Prefer", "respond-async"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdTasks").value(3));

        // Sin la cabecera, o al previsualizar, es sincrona: nada pasa por las operaciones de fondo.
        when(taskService.generatePreventiveTasks(eq(orderId), any())).thenReturn(generated);
        mockMvc.perform(post(ORDERS + "/{id}/tasks/generate", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .header("Prefer", "respond-async").contentType(MediaType.APPLICATION_JSON).content("{\"dryRun\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(ORDERS + "/{id}/tasks/generate", orderId).with(role(SecurityRoles.MAINTENANCE_WRITE)))
                .andExpect(status().isOk());
        verify(operations, times(2)).run(any(), any(), anyInt(), any(), any());
        verify(taskService, times(2)).generatePreventiveTasks(eq(orderId), any());
    }

    @Test
    void malformedIdsAndBodiesAnswer400AndAReaderCannotWrite() throws Exception {
        mockMvc.perform(get(ORDERS + "/{id}", "not-a-uuid").with(role(SecurityRoles.MAINTENANCE_READ)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQ-400"))
                .andExpect(jsonPath("$.validationErrors[0].field").value("id"));

        mockMvc.perform(put(ORDERS + "/{id}", UUID.randomUUID()).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQ-400"));

        mockMvc.perform(post(ORDERS).with(role(SecurityRoles.MAINTENANCE_READ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Preventive T2\",\"type\":\"PREVENTIVE\",\"assetId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH-403"));
        verifyNoInteractions(orderService);
    }

    private static MaterialUsageResponse materialResponse(UUID id, UUID orderId, UUID warehouseId) {
        return new MaterialUsageResponse(id, orderId, null, UUID.randomUUID(), "GA70", null, warehouseId, new BigDecimal("2"), BigDecimal.ZERO, "ud",
                false, null, StockSyncStatus.NOT_REQUESTED, null, null, null, null);
    }

    private static MaintenanceTaskResponse taskResponse(UUID id, UUID orderId) {
        return new MaintenanceTaskResponse(id, orderId, 1, "Profile 12-2.27", MaintenanceTaskStatus.PENDING, null, null, null, null, null, null, null,
                List.of(), List.of("RG-01"), List.of(), null, null);
    }

    /**
     * Las plantillas las mantiene quien supervisa: maintenance-supervise ademas de maintenance-write, las dos
     * mitades, en el alta de una version y en su PATCH. La lectura sigue con maintenance-read.
     */
    @Test
    void anInspectionTemplateIsWrittenWithSuperviseOnTopOfWriteAndItsBodyIsValidated() throws Exception {
        String templates = "/api/v1/maintenance/inspection-templates";
        UUID id = UUID.randomUUID();
        InspectionTemplateResponse version = new InspectionTemplateResponse(id, CatenaryAssetType.DISCONNECTOR, 2, "Disconnector inspection", true,
                List.of(new InspectionTemplateItemResponse(UUID.randomUUID(), "CONTINUITY", "Electrical continuity", "uOhm", null, new BigDecimal("100.000"), true, 1)),
                0L, null);
        when(templateService.create(any(InspectionTemplateRequest.class))).thenReturn(version);
        when(templateService.patch(eq(id), any())).thenReturn(version);
        String body = """
                {"assetType": "DISCONNECTOR", "name": "Disconnector inspection", "replacesVersion": 1,
                 "items": [{"code": "CONTINUITY", "label": "Electrical continuity", "unit": "uOhm", "maxValue": 100, "requiresMeasure": true}]}
                """;

        mockMvc.perform(post(templates).with(role(SecurityRoles.MAINTENANCE_WRITE)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("AUTH-403"));
        mockMvc.perform(post(templates).with(role(SecurityRoles.MAINTENANCE_SUPERVISE)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(templates + "/{id}", id).with(role(SecurityRoles.MAINTENANCE_WRITE))
                        .contentType(MergePatch.MEDIA_TYPE).content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(templates + "/{id}", id).with(role(SecurityRoles.MAINTENANCE_SUPERVISE))
                        .contentType(MergePatch.MEDIA_TYPE).content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(templateService);

        mockMvc.perform(post(templates).with(role(SecurityRoles.MAINTENANCE_WRITE, SecurityRoles.MAINTENANCE_SUPERVISE))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", templates + "/" + id))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.lockVersion").value(0))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.items[0].code").value("CONTINUITY"))
                .andExpect(jsonPath("$.items[0].orderIndex").value(1));
        verify(templateService).create(argThat(request -> request.replacesVersion() == 1 && request.items().size() == 1
                && new BigDecimal("100").compareTo(request.items().getFirst().maxValue()) == 0));

        // Lo que no cabe en las columnas o no es un punto, 400 en su campo, sin llegar al servicio.
        mockMvc.perform(post(templates).with(role(SecurityRoles.MAINTENANCE_WRITE, SecurityRoles.MAINTENANCE_SUPERVISE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetType\":\"PROFILE\",\"name\":\"Profile\",\"items\":[{\"code\":\"cw height\",\"label\":\"\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQ-VALIDATION"))
                .andExpect(jsonPath("$.validationErrors[*].field", org.hamcrest.Matchers.containsInAnyOrder("items[0].code", "items[0].label")));
        mockMvc.perform(post(templates).with(role(SecurityRoles.MAINTENANCE_WRITE, SecurityRoles.MAINTENANCE_SUPERVISE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assetType\":\"PROFILE\",\"name\":\"Profile\",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[0].field").value("items"));

        // El PATCH: lockVersion a null es no mandarlo; los puntos no son un campo suyo; una version vieja es 409.
        mockMvc.perform(patch(templates + "/{id}", id).with(role(SecurityRoles.MAINTENANCE_WRITE, SecurityRoles.MAINTENANCE_SUPERVISE))
                        .contentType(MergePatch.MEDIA_TYPE).content("{\"active\":true,\"name\":\"Back\",\"lockVersion\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
        verify(templateService).patch(eq(id), argThat(patch -> Boolean.TRUE.equals(patch.values().active()) && "Back".equals(patch.values().name())
                && patch.values().lockVersion() == 3L && patch.cleared().isEmpty()));
        mockMvc.perform(patch(templates + "/{id}", id).with(role(SecurityRoles.MAINTENANCE_WRITE, SecurityRoles.MAINTENANCE_SUPERVISE))
                        .contentType(MergePatch.MEDIA_TYPE).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VAL-001"));
        mockMvc.perform(patch(templates + "/{id}", id).with(role(SecurityRoles.MAINTENANCE_WRITE, SecurityRoles.MAINTENANCE_SUPERVISE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isUnsupportedMediaType());
        when(templateService.patch(eq(id), any())).thenThrow(new StaleVersionException("Inspection template was changed by someone else"));
        mockMvc.perform(patch(templates + "/{id}", id).with(role(SecurityRoles.MAINTENANCE_WRITE, SecurityRoles.MAINTENANCE_SUPERVISE))
                        .contentType(MergePatch.MEDIA_TYPE).content("{\"active\":true,\"lockVersion\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CON-001"));

        // Nada se borra, y leer sigue siendo de quien lee.
        mockMvc.perform(delete(templates + "/{id}", id).with(role(SecurityRoles.MAINTENANCE_WRITE, SecurityRoles.MAINTENANCE_SUPERVISE, SecurityRoles.MAINTENANCE_DELETE)))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get(templates).with(role(SecurityRoles.MAINTENANCE_READ))).andExpect(status().isOk());
    }
}
