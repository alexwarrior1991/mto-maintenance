package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataEntityNames;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.SectionInsulatorInstallation;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetSwitchRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Aislador de seccion: inspeccion propia. Cuelga de una estacion y trae su propio 'enabled'.
 *
 * <p>Es el unico activo que se situa sobre <b>agujas</b>: normalmente separa las catenarias de dos
 * vias que conectan por una, y a veces esta en medio de una sola. El equipo que va de noche
 * necesita saber sobre que aguja, en que KP y con que tangente, asi que el snapshot se las trae
 * todas —es el unico manejador que declara {@code ownsSwitches()}—.
 */
@Service
class SectionInsulatorMasterDataHandler extends AbstractAssetMasterDataHandler {

    static final String CODE_PREFIX = "SIN-";

    SectionInsulatorMasterDataHandler(CatenaryAssetRepository repository,
                                      CatenaryAssetSwitchRepository switchRepository) {
        super(repository, switchRepository);
    }

    @Override
    public String entityName() {
        return MasterDataEntityNames.SECTION_INSULATOR;
    }

    @Override
    protected boolean ownsSwitches() {
        return true;
    }

    @Override
    protected AssetSnapshot snapshot(MasterDataPayload payload, String sourceEntityId) {
        List<SwitchSnapshot> switches = readSwitches(payload);
        List<BigDecimal> kps = kilometricPoints(payload.decimal("kp"), switches);

        return new AssetSnapshot(
                CatenaryAssetType.SECTION_INSULATOR,
                CODE_PREFIX,
                payload.string("name"),
                null,
                payload.nested("track").longValue("id"),
                payload.nested("connectedTrack").longValue("id"),
                payload.nested("station").longValue("id"),
                kps.isEmpty() ? null : kps.getFirst(),
                kps.isEmpty() ? null : kps.getLast(),
                null,
                null,
                installationType(payload.string("installationType")),
                switches,
                payload.bool("enabled", true)
        );
    }

    private List<SwitchSnapshot> readSwitches(MasterDataPayload payload) {
        return payload.nestedList("switches").stream()
                .map(each -> new SwitchSnapshot(
                        each.string("code"),
                        each.decimal("kp"),
                        each.integerValue("turnoutDenominator"),
                        each.longValue("trackId")))
                .filter(each -> each.code() != null && !each.code().isBlank())
                .toList();
    }

    /**
     * Los KP conocidos del aislador, ordenados: el suyo propio y el de cada aguja.
     *
     * <p>El primero y el ultimo acaban en {@code start_kp} y {@code end_kp}. Ordenar es lo que
     * garantiza que {@code chk_catenary_asset_kp_range} ({@code start_kp <= end_kp}) no pueda saltar
     * nunca, pase lo que pase con el orden en que llegan las agujas.
     *
     * <p>Por que un rango y no un punto: un aislador sobre una conexion entre vias esta realmente
     * repartido entre los KP de sus agujas, y con el rango aparece en {@code kpBetween} y en
     * {@code findEnabledByTypeOnTrackBetween}, que es lo que lo pone al alcance de un preventivo
     * sobre ese tramo. Sin KP ninguno se queda a null, como hasta ahora.
     */
    private static List<BigDecimal> kilometricPoints(BigDecimal own, List<SwitchSnapshot> switches) {
        List<BigDecimal> kps = new ArrayList<>();

        if (own != null) {
            kps.add(own);
        }
        switches.stream().map(SwitchSnapshot::kp).filter(Objects::nonNull).forEach(kps::add);

        kps.sort(Comparator.naturalOrder());
        return kps;
    }

    /** Lectura tolerante: un valor que este lado no conoce se guarda como nada, nunca va a la DLQ. */
    private static SectionInsulatorInstallation installationType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        for (SectionInsulatorInstallation each : SectionInsulatorInstallation.values()) {
            if (each.name().equalsIgnoreCase(value.trim())) {
                return each;
            }
        }

        return null;
    }
}
