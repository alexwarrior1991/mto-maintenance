package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateResponse;

import java.util.List;
import java.util.UUID;

public interface InspectionTemplateService {

    List<InspectionTemplateResponse> findAll();

    InspectionTemplateResponse findById(UUID id);
}
