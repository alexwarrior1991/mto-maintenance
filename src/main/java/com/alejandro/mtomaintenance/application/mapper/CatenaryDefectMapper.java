package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryDefect;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructCentralConfig.class, uses = {AuditableMapper.class, CatenaryAssetMapper.class})
public interface CatenaryDefectMapper {

    @Mapping(target = "audit", source = "defect")
    @Mapping(target = "inspectionId", source = "inspection.id")
    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "resolvedInShiftId", source = "resolvedInShift.id")
    @Mapping(target = "foundInTaskId", source = "foundInTask.id")
    CatenaryDefectResponse toResponse(CatenaryDefect defect);
}
