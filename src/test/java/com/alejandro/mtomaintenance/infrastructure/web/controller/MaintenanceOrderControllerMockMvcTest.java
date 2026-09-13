package com.alejandro.mtomaintenance.infrastructure.web.controller;

import com.alejandro.mtomaintenance.application.dto.order.CancelOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.CompleteOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderResponse;
import com.alejandro.mtomaintenance.application.exception.InvalidTransitionException;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.service.MaintenanceMaterialUsageService;
import com.alejandro.mtomaintenance.application.service.MaintenanceOrderService;
import com.alejandro.mtomaintenance.application.service.MaintenanceTaskService;
import com.alejandro.mtomaintenance.application.service.StatusHistoryService;
import com.alejandro.mtomaintenance.configuration.security.RestAccessDeniedHandler;
import com.alejandro.mtomaintenance.configuration.security.RestAuthenticationEntryPoint;
import com.alejandro.mtomaintenance.configuration.security.SecurityAuthorityPrefixes;
import com.alejandro.mtomaintenance.configuration.security.SecurityConfiguration;
import com.alejandro.mtomaintenance.configuration.security.SecurityRoles;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import com.alejandro.mtomaintenance.infrastructure.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
 * los codigos de error estables.
 */
@WebMvcTest(MaintenanceOrderController.class)
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
                0, 0, BigDecimal.ZERO, 0, null);
    }
}
