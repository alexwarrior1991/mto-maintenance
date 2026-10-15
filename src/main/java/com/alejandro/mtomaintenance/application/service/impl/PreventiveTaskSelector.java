package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksRequest;
import com.alejandro.mtomaintenance.application.dto.task.GenerationOutcome;
import com.alejandro.mtomaintenance.application.dto.task.GenerationSelection;
import com.alejandro.mtomaintenance.application.exception.InvalidTransitionException;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DisconnectorDriveType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Lo que encaja en lo que se pide generar en un preventivo, y lo que pasa con cada activo. No crea
 * nada: lo crea {@code MaintenanceTaskServiceImpl} con lo que devuelve, o nada con {@code dryRun}.
 *
 * <p>Sin {@code selections} es lo de siempre: el tramo de la orden, con los tipos de activo de
 * {@code assetTypes}. Con ellas, cada una dice que tipo de activo y donde, dentro del EP de la orden:
 * una orden es de un solo EP (su proyecto de almacen es el del EP, y el informe mensual cuenta sus
 * tareas en el) y puede abarcar varias de sus vias. Lo de otro EP va en otra orden.</p>
 *
 * <p>Que no se duplique: un activo aparece una vez aunque lo encuentren dos selecciones (vale la
 * primera); el que ya tiene tarea en la orden, tambien cancelada, se salta como siempre; y el que tiene
 * una tarea abierta en otra orden preventiva sin terminar, tambien. Un activo sin EP (un hueco de los
 * datos maestros) no se descarta por EP: no se sabe de cual es, y se enseña en la previsualizacion.</p>
 */
@Component
@RequiredArgsConstructor
class PreventiveTaskSelector {

    /** Lo que se genera uno a uno; un tramo no. */
    static final Set<CatenaryAssetType> GENERATED_ASSET_TYPES =
            EnumSet.of(CatenaryAssetType.PROFILE, CatenaryAssetType.SECTION_INSULATOR, CatenaryAssetType.DISCONNECTOR);

    private final CatenaryAssetRepository assets;
    private final MaintenanceTaskRepository tasks;
    private final MaintenanceLookups lookups;

    /** Un activo que encaja, con lo que llevaria su tarea y lo que pasa con el. */
    record Candidate(CatenaryAsset asset, Set<MaintenanceTaskType> taskTypes, boolean withChecklist,
                     GenerationOutcome outcome, String otherOrderCode) {

        boolean isCreated() {
            return outcome == GenerationOutcome.CREATE;
        }
    }

    /** Todo lo que encaja, en el orden en que se recorre, y los tipos de activo que se pidieron. */
    record Plan(List<Candidate> candidates, Set<CatenaryAssetType> assetTypes) {
    }

    /**
     * Una parte de lo pedido ya resuelta: sus activos y lo que llevan. {@code routeTrack} es la via por
     * la que se recorre: la de la seleccion, o sin ella la de cada activo.
     */
    private record Scope(CatenaryAssetType type, Long routeTrack, List<CatenaryAsset> found, List<String> taskTypeCodes,
                         boolean withChecklist) {
    }

    private record Placed(CatenaryAsset asset, Scope scope, Long routeTrack) {
    }

    Plan select(MaintenanceOrder order, GeneratePreventiveTasksRequest request) {
        List<Scope> scopes = request.hasSelections() ? selectionScopes(order, request) : sectionScopes(order, request);
        List<Placed> placed = place(scopes);

        Set<UUID> inOrder = new HashSet<>(tasks.findAssetIdsByOrderId(order.getId()));
        List<UUID> free = placed.stream()
                .map(Placed::asset)
                .filter(asset -> !inOrder.contains(asset.getId()) && !ofAnotherPackage(asset, order))
                .map(CatenaryAsset::getId)
                .toList();
        Map<UUID, String> elsewhere = new HashMap<>();
        tasks.findOpenPreventiveTasksElsewhere(free, order.getId())
                .forEach(found -> elsewhere.putIfAbsent(found.getAssetId(), found.getOrderCode()));

        Map<List<String>, Set<MaintenanceTaskType>> typesByCodes = new HashMap<>();
        List<Candidate> candidates = new ArrayList<>();
        for (Placed each : placed) {
            CatenaryAsset asset = each.asset();
            List<String> codes = each.scope().taskTypeCodes() != null ? each.scope().taskTypeCodes() : defaultTaskTypes(asset);
            Set<MaintenanceTaskType> types = codes.isEmpty() ? Set.of() : typesByCodes.computeIfAbsent(codes, lookups::taskTypes);
            GenerationOutcome outcome;
            if (inOrder.contains(asset.getId())) {
                outcome = GenerationOutcome.ALREADY_IN_ORDER;
            } else if (ofAnotherPackage(asset, order)) {
                outcome = GenerationOutcome.OTHER_PACKAGE;
            } else if (elsewhere.containsKey(asset.getId())) {
                outcome = GenerationOutcome.IN_OTHER_ORDER;
            } else if (types.isEmpty()) {
                outcome = GenerationOutcome.NO_TASK_TYPES;
            } else {
                outcome = GenerationOutcome.CREATE;
            }
            candidates.add(new Candidate(asset, types, each.scope().withChecklist(), outcome, elsewhere.get(asset.getId())));
        }
        Set<CatenaryAssetType> types = EnumSet.noneOf(CatenaryAssetType.class);
        scopes.forEach(scope -> types.add(scope.type()));
        return new Plan(candidates, types);
    }

    /**
     * Sin tipos elegidos, los del plan para cada activo: los del preventivo en un perfil; RG-03 y RP-02
     * en un aislador, y RG-04 y RP-03 si tiene agujas (sus {@code switches}, tambien las fuera de
     * servicio, que siguen en el plano); RP-07 en un seccionador, RP-06 ademas si tiene motor y RG-06
     * ademas si es una autovalvula. Ningun activo generado se queda sin tipo.
     */
    static List<String> defaultTaskTypes(CatenaryAsset asset) {
        return switch (asset.getType()) {
            case SECTION_INSULATOR -> asset.getSwitches() == null || asset.getSwitches().isEmpty()
                    ? MaintenanceTaskServiceImpl.DEFAULT_INSULATOR_TASK_TYPES
                    : MaintenanceTaskServiceImpl.DEFAULT_INSULATOR_WITH_SWITCHES_TASK_TYPES;
            case DISCONNECTOR -> disconnectorTaskTypes(asset);
            default -> MaintenanceTaskServiceImpl.DEFAULT_PREVENTIVE_TASK_TYPES;
        };
    }

    /** RP-07, RP-06 con motor y RG-06 en una autovalvula, en el orden del codigo. */
    private static List<String> disconnectorTaskTypes(CatenaryAsset asset) {
        List<String> codes = asset.getDriveType() == DisconnectorDriveType.MOTOR
                ? MaintenanceTaskServiceImpl.DEFAULT_MOTOR_DISCONNECTOR_TASK_TYPES
                : MaintenanceTaskServiceImpl.DEFAULT_DISCONNECTOR_TASK_TYPES;
        if (!MaintenanceTaskServiceImpl.SURGE_ARRESTER_FUNCTION.equalsIgnoreCase(asset.getDisconnectorFunction())) {
            return codes;
        }
        List<String> withValve = new ArrayList<>();
        withValve.add(MaintenanceTaskServiceImpl.SURGE_ARRESTER_TASK_TYPE);
        withValve.addAll(codes);
        return List.copyOf(withValve);
    }

    /** De otro EP que la orden; uno sin EP no se sabe, y no se descarta. */
    private static boolean ofAnotherPackage(CatenaryAsset asset, MaintenanceOrder order) {
        return order.getExecutionPackageId() != null && asset.getExecutionPackageId() != null
                && !order.getExecutionPackageId().equals(asset.getExecutionPackageId());
    }

    /**
     * Cada activo una vez, en el orden en que se recorre: por via, en el orden en que aparecen las vias
     * en lo pedido, y dentro de cada una por kp y codigo. Asi un aislador queda entre los perfiles que
     * lo rodean, tambien el de otra via que conecta con esta.
     */
    private static List<Placed> place(List<Scope> scopes) {
        Map<UUID, Placed> byAsset = new LinkedHashMap<>();
        Map<Long, Integer> trackRank = new HashMap<>();
        for (Scope scope : scopes) {
            for (CatenaryAsset asset : scope.found()) {
                Long route = scope.routeTrack() != null ? scope.routeTrack() : asset.getTrackId();
                if (route != null) {
                    trackRank.putIfAbsent(route, trackRank.size());
                }
                byAsset.putIfAbsent(asset.getId(), new Placed(asset, scope, route));
            }
        }
        Function<Placed, Integer> rank = each -> each.routeTrack() == null ? Integer.MAX_VALUE : trackRank.get(each.routeTrack());
        return byAsset.values().stream()
                .sorted(Comparator.comparing(rank)
                        .thenComparing(each -> each.asset().getStartKp(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(each -> each.asset().getCode()))
                .toList();
    }

    // ------------------------------------------------------------------ sin selecciones: el tramo

    private List<Scope> sectionScopes(MaintenanceOrder order, GeneratePreventiveTasksRequest request) {
        CatenaryAsset section = order.getAsset();
        if (section.getType() != CatenaryAssetType.TRACK_SECTION) {
            throw new InvalidTransitionException("Order " + order.getCode()
                    + " is not on a track section: say what to generate and where with selections");
        }
        if (section.getTrackId() == null || section.getStartKp() == null || section.getEndKp() == null) {
            throw new ValidationException("Track section " + section.getCode() + " has no track or kp range");
        }
        List<String> codes = chosen(request.taskTypeCodes());
        boolean withChecklist = Boolean.TRUE.equals(request.withChecklist());
        return legacyAssetTypes(request.assetTypes()).stream()
                .map(type -> new Scope(type, section.getTrackId(), onSection(type, section), codes, withChecklist))
                .toList();
    }

    /** Sin tipos de activo, los perfiles, que es lo que hacia antes de admitirlos; un tramo no se genera. */
    private static List<CatenaryAssetType> legacyAssetTypes(List<CatenaryAssetType> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of(CatenaryAssetType.PROFILE);
        }
        Set<CatenaryAssetType> types = EnumSet.copyOf(requested);
        if (!GENERATED_ASSET_TYPES.containsAll(types)) {
            throw new ValidationException("assetTypes: only " + GENERATED_ASSET_TYPES + " are generated one by one, not "
                    + types.stream().filter(type -> !GENERATED_ASSET_TYPES.contains(type)).toList());
        }
        // Los perfiles primero; el orden final es el de la via.
        return types.stream().sorted(Comparator.comparing(type -> type == CatenaryAssetType.PROFILE ? 0 : 1)).toList();
    }

    /**
     * Los perfiles de la via del tramo entre sus kp; un aislador o un seccionador, tambien si es de otra
     * via que conecta con esta y si su rango se solapa con el del tramo.
     */
    private List<CatenaryAsset> onSection(CatenaryAssetType type, CatenaryAsset section) {
        return type == CatenaryAssetType.PROFILE
                ? assets.findEnabledByTypeOnTrackBetween(type, section.getTrackId(), section.getStartKp(), section.getEndKp())
                : assets.findEnabledByTypeTouchingSection(type, section.getTrackId(), section.getStartKp(), section.getEndKp());
    }

    // ------------------------------------------------------------------ con selecciones

    private List<Scope> selectionScopes(MaintenanceOrder order, GeneratePreventiveTasksRequest request) {
        if (request.assetTypes() != null && !request.assetTypes().isEmpty()) {
            throw new ValidationException("assetTypes: not together with selections; each selection has its assetType");
        }
        if (order.getExecutionPackageId() == null) {
            throw new ValidationException("selections: order " + order.getCode() + " has no execution package, and an order "
                    + "mixes only assets of its own; generate its section without selections");
        }
        List<String> requestCodes = chosen(request.taskTypeCodes());
        List<GenerationSelection> selections = request.selections();
        return IntStream.range(0, selections.size())
                .mapToObj(index -> scope(order, selections.get(index), "selections[" + index + "].", requestCodes, request.withChecklist()))
                .toList();
    }

    private Scope scope(MaintenanceOrder order, GenerationSelection selection, String field, List<String> requestCodes,
                        Boolean requestChecklist) {
        CatenaryAssetType type = selection.assetType();
        if (!GENERATED_ASSET_TYPES.contains(type)) {
            throw new ValidationException(field + "assetType: only " + GENERATED_ASSET_TYPES + " are generated one by one, not " + type);
        }
        boolean byIds = selection.assetIds() != null && !selection.assetIds().isEmpty();
        long scopes = (selection.sectionId() != null ? 1 : 0) + (selection.trackId() != null ? 1 : 0) + (byIds ? 1 : 0);
        if (scopes > 1) {
            throw new ValidationException(field.substring(0, field.length() - 1) + ": one of sectionId, trackId or assetIds, not several");
        }
        if ((selection.fromKp() != null || selection.toKp() != null) && selection.trackId() == null) {
            throw new ValidationException(field + (selection.fromKp() != null ? "fromKp" : "toKp") + ": a kp range goes with a trackId");
        }
        if (selection.fromKp() != null && selection.toKp() != null && selection.fromKp().compareTo(selection.toKp()) > 0) {
            throw new ValidationException(field + "fromKp: " + selection.fromKp() + " is after toKp " + selection.toKp());
        }
        if (byIds && selection.stationId() != null) {
            throw new ValidationException(field + "stationId: the chosen assets are already chosen; a station filters the other scopes");
        }

        List<String> codes = selection.taskTypeCodes() != null && !selection.taskTypeCodes().isEmpty()
                ? List.copyOf(selection.taskTypeCodes())
                : requestCodes;
        boolean withChecklist = Boolean.TRUE.equals(selection.withChecklist() != null ? selection.withChecklist() : requestChecklist);
        Long packageId = order.getExecutionPackageId();

        if (selection.sectionId() != null) {
            CatenaryAsset section = section(order, selection.sectionId(), field);
            List<CatenaryAsset> found = onSection(type, section).stream()
                    .filter(asset -> selection.stationId() == null || selection.stationId().equals(asset.getStationId()))
                    .toList();
            return new Scope(type, section.getTrackId(), found, codes, withChecklist);
        }
        if (selection.trackId() != null) {
            return new Scope(type, selection.trackId(), assets.findEnabledForGeneration(type, selection.trackId(),
                    selection.fromKp(), selection.toKp(), selection.stationId(), null), codes, withChecklist);
        }
        if (byIds) {
            return new Scope(type, null, chosenAssets(order, type, selection.assetIds(), field), codes, withChecklist);
        }
        return new Scope(type, null, assets.findEnabledForGeneration(type, null, null, null, selection.stationId(), packageId),
                codes, withChecklist);
    }

    /**
     * Otro tramo del EP. Tiene que ser de la misma clase que el de la orden, porque la posesion que exige
     * una via desviada la decide la orden ({@code ShiftRules.requireCompatiblePossession}): una orden que
     * no esta sobre un tramo es de via principal.
     */
    private CatenaryAsset section(MaintenanceOrder order, UUID sectionId, String field) {
        CatenaryAsset section = assets.findById(sectionId)
                .orElseThrow(() -> new ValidationException(field + "sectionId: no asset " + sectionId));
        if (section.getType() != CatenaryAssetType.TRACK_SECTION) {
            throw new ValidationException(field + "sectionId: " + section.getCode() + " is a " + section.getType() + ", not a track section");
        }
        if (!section.isEnabled()) {
            throw new ValidationException(field + "sectionId: " + section.getCode() + " is disabled");
        }
        if (!Objects.equals(section.getExecutionPackageId(), order.getExecutionPackageId())) {
            throw new ValidationException(field + "sectionId: " + section.getCode() + " is not in the execution package of order "
                    + order.getCode() + "; it goes in another order");
        }
        if (section.getTrackId() == null || section.getStartKp() == null || section.getEndKp() == null) {
            throw new ValidationException(field + "sectionId: " + section.getCode() + " has no track or kp range");
        }
        TrackKind kind = section.getTrackKind() == null ? TrackKind.MAIN : section.getTrackKind();
        if (kind != kindOf(order)) {
            throw new ValidationException(field + "sectionId: " + section.getCode() + " is a " + kind + " track and order "
                    + order.getCode() + " works a " + kindOf(order) + " one; diverted and main tracks go in different orders");
        }
        return section;
    }

    private static TrackKind kindOf(MaintenanceOrder order) {
        CatenaryAsset asset = order.getAsset();
        return asset.getType() == CatenaryAssetType.TRACK_SECTION && asset.getTrackKind() != null ? asset.getTrackKind() : TrackKind.MAIN;
    }

    /** Elegidos a mano: tienen que existir, estar en servicio, ser del tipo de la seleccion y del EP de la orden. */
    private List<CatenaryAsset> chosenAssets(MaintenanceOrder order, CatenaryAssetType type, List<UUID> ids, String field) {
        List<UUID> distinct = ids.stream().distinct().toList();
        Map<UUID, CatenaryAsset> found = assets.findAllById(distinct).stream()
                .collect(Collectors.toMap(CatenaryAsset::getId, Function.identity()));
        List<UUID> missing = distinct.stream().filter(id -> !found.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new ValidationException(field + "assetIds: no assets " + missing);
        }
        List<CatenaryAsset> chosen = distinct.stream().map(found::get).toList();
        rejectChosen(chosen, asset -> asset.getType() != type, field, "are not " + type);
        rejectChosen(chosen, asset -> !asset.isEnabled(), field, "are disabled");
        rejectChosen(chosen, asset -> ofAnotherPackage(asset, order), field,
                "are not in the execution package of order " + order.getCode() + "; they go in another order");
        return chosen;
    }

    private static void rejectChosen(List<CatenaryAsset> chosen, Predicate<CatenaryAsset> wrong, String field, String why) {
        List<String> codes = chosen.stream().filter(wrong).map(CatenaryAsset::getCode).toList();
        if (!codes.isEmpty()) {
            throw new ValidationException(field + "assetIds: " + String.join(", ", codes) + " " + why);
        }
    }

    /** Los tipos elegidos, o null para que cada activo lleve los suyos. */
    private static List<String> chosen(List<String> codes) {
        return codes == null || codes.isEmpty() ? null : List.copyOf(codes);
    }
}
