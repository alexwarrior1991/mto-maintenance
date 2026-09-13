package com.alejandro.mtomaintenance.infrastructure.web.exception;

import com.alejandro.mtomaintenance.application.dto.error.ApiErrorResponse;
import com.alejandro.mtomaintenance.application.exception.AssetDisabledException;
import com.alejandro.mtomaintenance.application.exception.DuplicateCodeException;
import com.alejandro.mtomaintenance.application.exception.InspectionException;
import com.alejandro.mtomaintenance.application.exception.InvalidTransitionException;
import com.alejandro.mtomaintenance.application.exception.MaterialUsageException;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.exception.ShiftException;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundCarriesAggregatePrefixedCodeAndRequestContext() {
        MockHttpServletRequest request = request("GET", "/api/v1/maintenance/orders/" + UUID.randomUUID());
        request.addHeader("X-Correlation-Id", "corr-1");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleNotFound(new NotFoundException("Maintenance order", UUID.randomUUID()), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ORD-404", response.getBody().errorCode());
        assertEquals("corr-1", response.getBody().correlationId());
        assertEquals("GET", response.getBody().method());
    }

    @Test
    void stateConflictsAnswer409WithTheirOwnCodes() {
        MockHttpServletRequest request = request("POST", "/api/v1/maintenance/orders");

        assertEquals("TRN-001", codeOf(handler.handleStateConflict(new InvalidTransitionException("no"), request), HttpStatus.CONFLICT));
        assertEquals("AST-001", codeOf(handler.handleStateConflict(new AssetDisabledException("no"), request), HttpStatus.CONFLICT));
        assertEquals("SHF-001", codeOf(handler.handleStateConflict(new ShiftException("no"), request), HttpStatus.CONFLICT));
        assertEquals("MAT-001", codeOf(handler.handleStateConflict(new MaterialUsageException("no"), request), HttpStatus.CONFLICT));
        assertEquals("AST-409", codeOf(handler.handleDuplicateCode(new DuplicateCodeException("Catenary asset", "SEC-1"), request), HttpStatus.CONFLICT));
    }

    @Test
    void inspectionRulesAnswer422AndStockOutagesAnswer503() {
        MockHttpServletRequest request = request("POST", "/api/v1/maintenance/inspections");

        assertEquals("INS-001", codeOf(handler.handleInspection(new InspectionException("no"), request), HttpStatus.UNPROCESSABLE_CONTENT));
        assertEquals("STK-503", codeOf(handler.handleStockUnavailable(new StockUnavailableException("down"), request), HttpStatus.SERVICE_UNAVAILABLE));
        assertEquals("VAL-001", codeOf(handler.handleValidation(new ValidationException("bad"), request), HttpStatus.BAD_REQUEST));
    }

    @Test
    void unexpectedExceptionsDoNotLeakDetails() {
        MockHttpServletRequest request = request("GET", "/api/v1/maintenance/orders");

        ResponseEntity<ApiErrorResponse> response = handler.handleException(new IllegalStateException("secret"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("APP-500", response.getBody().errorCode());
        assertEquals("An unexpected error occurred. Please contact support.", response.getBody().message());
    }

    private static String codeOf(ResponseEntity<ApiErrorResponse> response, HttpStatus expected) {
        assertEquals(expected, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody().errorCode();
    }

    private static MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }
}
