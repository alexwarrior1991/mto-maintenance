package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetSummaryResponse;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;

@Mapper(config = MapStructCentralConfig.class, uses = {AuditableMapper.class, MaintenanceTeamMapper.class, CatenaryAssetMapper.class})
public interface MaintenanceShiftMapper {

    @Mapping(target = "audit", source = "shift")
    @Mapping(target = "trackIds", expression = "java(sortedTracks(shift))")
    @Mapping(target = "blockingDisconnectors", expression = "java(toSummaries(sortedDisconnectors(shift)))")
    MaintenanceShiftResponse toResponse(MaintenanceShift shift);

    /** La implementa MapStruct con {@link CatenaryAssetMapper#toSummary}. */
    List<CatenaryAssetSummaryResponse> toSummaries(List<CatenaryAsset> assets);

    default List<Long> sortedTracks(MaintenanceShift shift) {
        return shift.getTrackIds() == null ? List.of() : shift.getTrackIds().stream().sorted().toList();
    }

    /** Por codigo, para que la cabecera del parte los liste siempre en el mismo orden. */
    default List<CatenaryAsset> sortedDisconnectors(MaintenanceShift shift) {
        if (shift.getBlockingDisconnectors() == null) {
            return List.of();
        }
        return shift.getBlockingDisconnectors().stream()
                .sorted(Comparator.comparing(CatenaryAsset::getCode, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }
}
