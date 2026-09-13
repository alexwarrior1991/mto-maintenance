package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateItemResponse;
import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionTemplate;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionTemplateItem;
import org.mapstruct.Mapper;

@Mapper(config = MapStructCentralConfig.class)
public interface InspectionTemplateMapper {

    InspectionTemplateResponse toResponse(InspectionTemplate template);

    InspectionTemplateItemResponse toItemResponse(InspectionTemplateItem item);
}
