package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtomaintenance.application.dto.common.PageResponse;
import com.alejandro.mtomaintenance.application.dto.shift.CancelShiftRequest;
import com.alejandro.mtomaintenance.application.dto.shift.CloseShiftRequest;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftRequest;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftResponse;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportResponse;
import com.alejandro.mtomaintenance.application.dto.shift.StartShiftRequest;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface MaintenanceShiftService {

    MaintenanceShiftResponse create(MaintenanceShiftRequest request);

    MaintenanceShiftResponse update(UUID id, MaintenanceShiftUpdateRequest request);

    MaintenanceShiftResponse findById(UUID id);

    PageResponse<MaintenanceShiftResponse> search(LocalDate date, LocalDate dateFrom, LocalDate dateTo, UUID teamId, Long trackId,
                                                  Long executionPackageId, ShiftStatus status, PossessionType possessionType,
                                                  Pageable pageable);

    MaintenanceShiftResponse start(UUID id, StartShiftRequest request);

    MaintenanceShiftResponse close(UUID id, CloseShiftRequest request);

    MaintenanceShiftResponse cancel(UUID id, CancelShiftRequest request);

    ShiftReportResponse report(UUID id);

    PageResponse<EntityRevisionResponse<MaintenanceShiftResponse>> findRevisions(UUID id, Pageable pageable);
}
