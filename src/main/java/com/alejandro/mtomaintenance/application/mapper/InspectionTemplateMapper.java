package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateItemResponse;
import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionTemplate;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionTemplateItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructCentralConfig.class, uses = AuditableMapper.class)
public interface InspectionTemplateMapper {

    /** {@code version} sigue siendo el numero de version de la plantilla; el bloqueo optimista sale como {@code lockVersion}. */
    @Mapping(target = "version", source = "templateVersion")
    @Mapping(target = "lockVersion", source = "version")
    @Mapping(target = "audit", source = "template")
    InspectionTemplateResponse toResponse(InspectionTemplate template);

    InspectionTemplateItemResponse toItemResponse(InspectionTemplateItem item);
}
