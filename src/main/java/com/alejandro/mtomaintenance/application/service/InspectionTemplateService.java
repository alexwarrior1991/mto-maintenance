package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.common.MergePatch;
import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateResponse;
import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface InspectionTemplateService {

    List<InspectionTemplateResponse> findAll();

    InspectionTemplateResponse findById(UUID id);

    /** La version siguiente de la plantilla de un tipo de activo: nace activa y sustituye a la que lo era. */
    InspectionTemplateResponse create(InspectionTemplateRequest request);

    /** El nombre de una version y si es la activa de su tipo; sus puntos no cambian. */
    InspectionTemplateResponse patch(UUID id, MergePatch<InspectionTemplateUpdateRequest> patch);
}
