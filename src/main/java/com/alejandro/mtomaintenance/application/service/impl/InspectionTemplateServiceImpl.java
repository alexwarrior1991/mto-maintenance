package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateResponse;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.mapper.InspectionTemplateMapper;
import com.alejandro.mtomaintenance.application.service.InspectionTemplateService;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.InspectionTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class InspectionTemplateServiceImpl implements InspectionTemplateService {

    private final InspectionTemplateRepository repository;
    private final InspectionTemplateMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<InspectionTemplateResponse> findAll() {
        return repository.findAllByOrderByAssetTypeAscVersionDesc().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InspectionTemplateResponse findById(UUID id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Inspection template", id));
    }
}
