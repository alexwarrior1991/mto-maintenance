package com.alejandro.mtomaintenance.infrastructure.web.controller;

import com.alejandro.mtomaintenance.application.dto.export.ExportedReport;
import com.alejandro.mtomaintenance.application.dto.export.ReportFormat;
import com.alejandro.mtomaintenance.application.dto.export.ReportMediaTypes;
import com.alejandro.mtomaintenance.application.dto.report.ProgressReportResponse;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportResponse;
import com.alejandro.mtomaintenance.application.service.MaintenanceReportService;
import com.alejandro.mtomaintenance.application.service.MaintenanceShiftService;
import com.alejandro.mtomaintenance.application.service.MaintenanceTaskService;
import com.alejandro.mtomaintenance.application.service.ReportExportService;
import com.alejandro.mtomaintenance.configuration.security.RestAccessDeniedHandler;
import com.alejandro.mtomaintenance.configuration.security.RestAuthenticationEntryPoint;
import com.alejandro.mtomaintenance.configuration.security.SecurityAuthorityPrefixes;
import com.alejandro.mtomaintenance.configuration.security.SecurityConfiguration;
import com.alejandro.mtomaintenance.configuration.security.SecurityRoles;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;
import com.alejandro.mtomaintenance.infrastructure.web.exception.GlobalExceptionHandler;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El contrato HTTP de la descarga, con la cadena de seguridad real: que cada formato salga con su
 * tipo de contenido y su nombre de fichero, que sin parametro siga saliendo el JSON de siempre y que
 * un formato desconocido sea un 400 y no un 500.
 *
 * <p>Va en su propia clase y no en {@code MaintenanceOrderControllerMockMvcTest} porque un
 * {@code @WebMvcTest} solo levanta los controladores que se le nombran: el de ordenes no puede
 * alojar pruebas de estos dos. Es la misma razon por la que aquella clase existe aparte.</p>
 *
 * <p>Tambien es la comprobacion de que la exportacion no toca la seguridad: las rutas no cambian,
 * {@code ?format} es un parametro, y {@code SecurityConfiguration} decide por ruta y verbo. Que un
 * token con {@code MAINTENANCE_READ} se lleve el .xlsx se verifica aqui en vez de suponerlo.</p>
 */
@WebMvcTest({MaintenanceReportController.class, MaintenanceShiftController.class})
@Import({SecurityConfiguration.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class ReportExportControllerMockMvcTest {

    private static final String PROGRESS = "/api/v1/maintenance/reports/progress";
    private static final String MONTHLY = "/api/v1/maintenance/reports/monthly";
    private static final String SHIFTS = "/api/v1/maintenance/shifts";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MaintenanceReportService reportService;

    @MockitoBean
    private MaintenanceShiftService shiftService;

    @MockitoBean
    private MaintenanceTaskService taskService;

    @MockitoBean
    private ReportExportService reportExports;

    @Test
    void eachFormatIsServedAsItsOwnFileWithTheNameTheLayoutChose() throws Exception {
        UUID shiftId = UUID.randomUUID();
        when(reportService.progress(null, null, null, null, null)).thenReturn(progress());
        when(shiftService.report(shiftId)).thenReturn(shiftReport());
        when(reportExports.exportProgressReport(any(), eq(ReportFormat.XLSX))).thenReturn(
                new ExportedReport("progress-report-2026-01-31-ep6.xlsx", ReportMediaTypes.XLSX, bytes("PK-workbook")));
        when(reportExports.exportShiftReport(any(), eq(ReportFormat.PDF))).thenReturn(
                new ExportedReport("shift-report-2026-01-27-SH-000001.pdf", ReportMediaTypes.PDF, bytes("%PDF-1.4")));

        mockMvc.perform(get(PROGRESS).param("format", "xlsx").with(role(SecurityRoles.MAINTENANCE_READ)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", ReportMediaTypes.XLSX))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"progress-report-2026-01-31-ep6.xlsx\""))
                .andExpect(header().string("Content-Length", "11"));

        // En mayusculas para que quede fijado que el enlace funciona lo escriba quien lo escriba.
        mockMvc.perform(get(SHIFTS + "/{id}/report", shiftId).param("format", "PDF").with(role(SecurityRoles.MAINTENANCE_READ)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", ReportMediaTypes.PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"shift-report-2026-01-27-SH-000001.pdf\""))
                .andExpect(content().string("%PDF-1.4"));
    }

    @Test
    void withoutAFormatTheThreeEndpointsAnswerTheJsonTheyAlwaysAnswered() throws Exception {
        UUID shiftId = UUID.randomUUID();
        when(reportService.progress(null, null, null, null, null)).thenReturn(progress());
        when(shiftService.report(shiftId)).thenReturn(shiftReport());

        mockMvc.perform(get(PROGRESS).with(role(SecurityRoles.MAINTENANCE_READ)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.checkedAssets").value(318))
                .andExpect(header().doesNotExist("Content-Disposition"));

        mockMvc.perform(get(SHIFTS + "/{id}/report", shiftId).param("format", "json").with(role(SecurityRoles.MAINTENANCE_READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shift.code").value("SH-000001"))
                .andExpect(jsonPath("$.rows").isArray());

        mockMvc.perform(get(MONTHLY).param("month", "2026-01").with(role(SecurityRoles.MAINTENANCE_READ)))
                .andExpect(status().isOk());

        verifyNoInteractions(reportExports);
    }

    @Test
    void anUnknownFormatIsARejectedRequestAndNothingIsExported() throws Exception {
        when(reportService.progress(null, null, null, null, null)).thenReturn(progress());

        mockMvc.perform(get(PROGRESS).param("format", "csv").with(role(SecurityRoles.MAINTENANCE_READ)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VAL-001"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("xlsx")));

        verify(reportExports, never()).exportProgressReport(any(), any());
    }

    @Test
    void downloadingIsReadingSoTheReadRoleIsEnoughAndNoTokenIsNotEnough() throws Exception {
        when(reportService.progress(null, null, null, null, null)).thenReturn(progress());
        when(reportExports.exportProgressReport(any(), eq(ReportFormat.XLSX)))
                .thenReturn(new ExportedReport("progress-report.xlsx", ReportMediaTypes.XLSX, bytes("PK")));

        mockMvc.perform(get(PROGRESS).param("format", "xlsx").with(role(SecurityRoles.MAINTENANCE_READ)))
                .andExpect(status().isOk());
        mockMvc.perform(get(PROGRESS).param("format", "xlsx"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"));
        mockMvc.perform(get(PROGRESS).param("format", "xlsx").with(role(SecurityRoles.OPS_METRICS)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH-403"));
    }

    private static byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.US_ASCII);
    }

    private static RequestPostProcessor role(String... roles) {
        String[] authorities = new String[roles.length];
        for (int index = 0; index < roles.length; index++) {
            authorities[index] = SecurityAuthorityPrefixes.ROLE_PREFIX + roles[index];
        }
        return jwt().authorities(AuthorityUtils.createAuthorityList(authorities));
    }

    private static ProgressReportResponse progress() {
        return new ProgressReportResponse(null, null, 412, 318, new BigDecimal("0.7718"),
                new BigDecimal("17.240"), new BigDecimal("22.340"), List.of());
    }

    private static ShiftReportResponse shiftReport() {
        MaintenanceShiftResponse shift = new MaintenanceShiftResponse(UUID.randomUUID(), "SH-000001", LocalDate.of(2026, 1, 27),
                null, "Rishpon", null, PossessionType.PARTIAL, null, null, null, null, null, null,
                List.of(), null, null, 6L, List.of(2L), null, null, null, null, ShiftStatus.PLANNED, null, null);
        return new ShiftReportResponse(shift, 0, 0, 0, 0, 0, List.of());
    }
}
