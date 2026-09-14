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
import com.alejandro.mtomaintenance.application.dto.defect.DefectCommentRequest;
import com.alejandro.mtomaintenance.application.dto.error.ValidationError;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamRequest;
import com.alejandro.mtomaintenance.application.exception.BusinessException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void genericBusinessExceptionsAnswer422AndUnknownAggregatesFallBackToTheAppPrefix() {
        MockHttpServletRequest request = request("POST", "/api/v1/maintenance/teams");

        assertEquals("BUS-001", codeOf(handler.handleBusiness(new BusinessException("odd"), request), HttpStatus.UNPROCESSABLE_CONTENT));
        assertEquals("APP-404", codeOf(handler.handleNotFound(new NotFoundException("Widget", UUID.randomUUID()), request), HttpStatus.NOT_FOUND));
        assertEquals("TEA-409", codeOf(handler.handleDuplicateCode(new DuplicateCodeException("Maintenance team", "A"), request), HttpStatus.CONFLICT));
        assertEquals("SHF-404", codeOf(handler.handleNotFound(new NotFoundException("Maintenance shift", UUID.randomUUID()), request), HttpStatus.NOT_FOUND));
        assertEquals("TSK-404", codeOf(handler.handleNotFound(new NotFoundException("Maintenance task", UUID.randomUUID()), request), HttpStatus.NOT_FOUND));
    }

    @Test
    void securityFailuresAnswer401And403WithAFixedMessage() {
        MockHttpServletRequest request = request("GET", "/api/v1/maintenance/orders");

        ResponseEntity<ApiErrorResponse> unauthenticated = handler.handleAuthentication(new BadCredentialsException("token expired"), request);
        ResponseEntity<ApiErrorResponse> forbidden = handler.handleAccessDenied(new AccessDeniedException("needs supervise"), request);

        assertEquals("AUTH-401", codeOf(unauthenticated, HttpStatus.UNAUTHORIZED));
        assertEquals("Authentication is required to access this resource.", unauthenticated.getBody().message());
        assertEquals("AUTH-403", codeOf(forbidden, HttpStatus.FORBIDDEN));
        assertEquals("The authenticated user is not allowed to perform this operation.", forbidden.getBody().message(),
                "The cause of the denial is never echoed to the client");
    }

    @Test
    void malformedRequestsAnswer400OrTheirOwnHttpStatusNamingTheOffendingParameter() {
        MockHttpServletRequest request = request("GET", "/api/v1/maintenance/orders/not-a-uuid");
        MethodArgumentTypeMismatchException mismatch = new MethodArgumentTypeMismatchException("not-a-uuid", UUID.class, "id", null,
                new IllegalArgumentException("Invalid UUID"));

        ResponseEntity<ApiErrorResponse> response = handler.handleMethodArgumentTypeMismatch(mismatch, request);

        assertEquals("REQ-400", codeOf(response, HttpStatus.BAD_REQUEST));
        assertEquals(List.of(new ValidationError("id", "must be a valid UUID")), response.getBody().validationErrors());
        assertEquals("REQ-400", codeOf(handler.handleHttpMessageNotReadable(request), HttpStatus.BAD_REQUEST));
        assertEquals("HTTP-404", codeOf(handler.handleNoResourceFound(request), HttpStatus.NOT_FOUND));
        assertEquals("REQ-415", codeOf(handler.handleUnsupportedMediaType(new HttpMediaTypeNotSupportedException("text/plain"), request),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        assertEquals("/api/v1/maintenance/orders/not-a-uuid", response.getBody().path());
    }

    @Test
    void constraintViolationsAreListedSortedByPropertyPath() {
        MockHttpServletRequest request = request("POST", "/api/v1/maintenance/teams");
        var violations = Validation.buildDefaultValidatorFactory().getValidator()
                .validate(new MaintenanceTeamRequest(" ", "x".repeat(121), null, null, null, null));

        ResponseEntity<ApiErrorResponse> response = handler.handleConstraintViolation(new ConstraintViolationException(violations), request);

        assertEquals("REQ-VALIDATION", codeOf(response, HttpStatus.BAD_REQUEST));
        assertEquals(List.of("code", "name"), response.getBody().validationErrors().stream().map(ValidationError::field).toList());
        assertTrue(Validation.buildDefaultValidatorFactory().getValidator().validate(new DefectCommentRequest("ok")).isEmpty());
    }
}
