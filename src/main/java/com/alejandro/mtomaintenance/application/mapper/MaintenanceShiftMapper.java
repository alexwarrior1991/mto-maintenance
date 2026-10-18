package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetSummaryResponse;
import com.alejandro.mtomaintenance.application.dto.crew.CrewPersonSummary;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftInstrumentResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftVehicleResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CrewPerson;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.FleetVehicle;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInstrument;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShiftVehicle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;

@Mapper(config = MapStructCentralConfig.class, uses = {AuditableMapper.class, MaintenanceTeamMapper.class, CatenaryAssetMapper.class})
public interface MaintenanceShiftMapper {

    @Mapping(target = "audit", source = "shift")
    @Mapping(target = "trackIds", expression = "java(sortedTracks(shift))")
    @Mapping(target = "blockingDisconnectors", expression = "java(toSummaries(sortedDisconnectors(shift)))")
    @Mapping(target = "linemen", expression = "java(linemen(shift))")
    @Mapping(target = "vehicles", expression = "java(vehicles(shift))")
    @Mapping(target = "instruments", expression = "java(instruments(shift))")
    MaintenanceShiftResponse toResponse(MaintenanceShift shift);

    /** Por codigo, como los seccionadores: el parte los lista siempre igual. */
    default List<CrewPersonSummary> linemen(MaintenanceShift shift) {
        if (shift.getLinemen() == null) {
            return List.of();
        }
        return shift.getLinemen().stream().sorted(Comparator.comparing(CrewPerson::getCode)).map(CrewSummaries::of).toList();
    }

    default List<ShiftVehicleResponse> vehicles(MaintenanceShift shift) {
        if (shift.getVehicles() == null) {
            return List.of();
        }
        return shift.vehiclesByCode().stream().map(MaintenanceShiftMapper::vehicle).toList();
    }

    static ShiftVehicleResponse vehicle(MaintenanceShiftVehicle shiftVehicle) {
        FleetVehicle vehicle = shiftVehicle.getVehicle();
        ShiftVehicleResponse.Parking parking = shiftVehicle.hasParking()
                ? new ShiftVehicleResponse.Parking(shiftVehicle.getParkingSiteId(), shiftVehicle.getParkingTrackId(),
                        shiftVehicle.getParkingKp(), shiftVehicle.getParkingDescription())
                : null;
        return new ShiftVehicleResponse(vehicle.getId(), vehicle.getCode(), vehicle.getName(), vehicle.getMobility(), vehicle.getCategory(),
                vehicle.getAvailable(), parking);
    }

    /** Por codigo, como los vehiculos: la respuesta, el evento y el parte los listan igual. */
    default List<ShiftInstrumentResponse> instruments(MaintenanceShift shift) {
        if (shift.getInstruments() == null) {
            return List.of();
        }
        return shift.instrumentsByCode().stream().map(MaintenanceShiftMapper::instrument).toList();
    }

    static ShiftInstrumentResponse instrument(MaintenanceInstrument instrument) {
        return new ShiftInstrumentResponse(instrument.getId(), instrument.getCode(), instrument.getName(), instrument.getKind(),
                instrument.getCalibrationExpiresOn());
    }

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
