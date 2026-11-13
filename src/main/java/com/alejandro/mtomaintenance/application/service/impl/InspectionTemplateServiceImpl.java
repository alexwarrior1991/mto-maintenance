package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.common.MergePatch;
import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateItemRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateResponse;
import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateUpdateRequest;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.exception.StaleVersionException;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import com.alejandro.mtomaintenance.application.mapper.InspectionTemplateMapper;
import com.alejandro.mtomaintenance.application.service.InspectionTemplateService;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionTemplate;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionTemplateItem;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.InspectionTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Las plantillas de inspeccion por la API. Una version no cambia en lo que una inspeccion copia de ella
 * (su tipo, su numero, sus puntos): cambiar el checklist es crear la version siguiente, que sustituye a
 * la activa. Asi la inspeccion que guarda el id de su plantilla senala siempre a lo que copio, y las
 * versiones anteriores son la historia, sin tablas de mas. Nada se borra: las inspecciones las
 * referencian; una version se desactiva.
 *
 * <p>Toda escritura toma primero el cerrojo de aviso del tipo de activo, asi que dos a la vez se ponen en
 * fila; que haya como mucho una activa por tipo lo garantiza ademas el indice unico parcial
 * {@code uq_inspection_template_active_asset_type}. La activa anterior se desactiva y se confirma antes
 * de activar la nueva, dentro de la misma transaccion: quien crea una inspeccion entre medias ve la una
 * o la otra, nunca ninguna.</p>
 */
@Service
@RequiredArgsConstructor
class InspectionTemplateServiceImpl implements InspectionTemplateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InspectionTemplateServiceImpl.class);

    /**
     * Un PATCH no vacia nada. {@code lockVersion} a {@code null} es no mandarlo, como {@code version} en
     * los demas recursos (el lector del merge-patch solo conoce ese nombre).
     */
    private static final Set<String> CLEARABLE = Set.of("lockVersion");

    private final InspectionTemplateRepository repository;
    private final InspectionTemplateMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<InspectionTemplateResponse> findAll() {
        return repository.findAllByOrderByAssetTypeAscTemplateVersionDesc().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InspectionTemplateResponse findById(UUID id) {
        return repository.findById(id).map(mapper::toResponse).orElseThrow(() -> notFound(id));
    }

    @Override
    @Transactional
    public InspectionTemplateResponse create(InspectionTemplateRequest request) {
        requireCoherentItems(request.items());
        CatenaryAssetType assetType = request.assetType();
        lock(assetType);

        Optional<InspectionTemplate> active = repository.findFirstByAssetTypeAndActiveTrueOrderByTemplateVersionDesc(assetType);
        if (request.replacesVersion() != null) {
            Integer current = active.map(InspectionTemplate::getTemplateVersion).orElse(null);
            if (!request.replacesVersion().equals(current)) {
                throw new StaleVersionException("The inspection template of " + assetType + " was changed by someone else: "
                        + (current == null ? "it has no active version" : "its active version is " + current)
                        + " and the request replaces version " + request.replacesVersion() + "; read it again");
            }
        }
        active.ifPresent(this::deactivate);

        InspectionTemplate template = InspectionTemplate.builder()
                .assetType(assetType)
                .templateVersion(repository.findLastVersion(assetType) + 1)
                .name(PatchRules.requireText(request.name(), "name"))
                .active(true)
                .build();
        int position = 0;
        for (InspectionTemplateItemRequest item : request.items()) {
            template.getItems().add(InspectionTemplateItem.builder()
                    .template(template)
                    .code(item.code())
                    .label(item.label().trim())
                    .unit(item.unit() == null || item.unit().isBlank() ? null : item.unit().trim())
                    .minValue(item.minValue())
                    .maxValue(item.maxValue())
                    .requiresMeasure(Boolean.TRUE.equals(item.requiresMeasure()))
                    .orderIndex(++position)
                    .build());
        }
        InspectionTemplate saved = repository.saveAndFlush(template);
        LOGGER.info("Inspection template of {} is now version {} ({} items)", assetType, saved.getTemplateVersion(), saved.getItems().size());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InspectionTemplateResponse patch(UUID id, MergePatch<InspectionTemplateUpdateRequest> patch) {
        // El tipo se lee sin cargar la plantilla: el cerrojo va antes de leer quien es la activa.
        CatenaryAssetType assetType = repository.findAssetTypeById(id).orElseThrow(() -> notFound(id));
        lock(assetType);
        InspectionTemplate template = repository.findById(id).orElseThrow(() -> notFound(id));
        InspectionTemplateUpdateRequest request = patch.values();
        StaleVersionException.check("Inspection template of " + assetType + " version " + template.getTemplateVersion(),
                template.getVersion(), request.lockVersion());
        PatchRules.requireClearable(patch, CLEARABLE);

        if (request.name() != null) {
            template.setName(PatchRules.requireText(request.name(), "name"));
        }
        if (Boolean.TRUE.equals(request.active()) && !template.getActive()) {
            repository.findFirstByAssetTypeAndActiveTrueOrderByTemplateVersionDesc(assetType).ifPresent(this::deactivate);
            template.setActive(true);
            LOGGER.info("Inspection template of {} is back to version {}", assetType, template.getTemplateVersion());
        } else if (Boolean.FALSE.equals(request.active()) && template.getActive()) {
            template.setActive(false);
            LOGGER.info("Inspection template of {} version {} deactivated: the type has no active template", assetType, template.getTemplateVersion());
        }
        return mapper.toResponse(repository.saveAndFlush(template));
    }

    /** Se confirma ya: el indice unico parcial no admite dos activas ni un instante, y Hibernate ordenaria el alta delante. */
    private void deactivate(InspectionTemplate template) {
        template.setActive(false);
        repository.saveAndFlush(template);
    }

    private void lock(CatenaryAssetType assetType) {
        repository.lockAssetType(AdvisoryLockKeys.of("mto-maintenance:inspection-template:" + assetType.name()));
    }

    /** Lo que Bean Validation no ve de la lista entera: un codigo repetido y un rango al reves. 400 {@code VAL-001}. */
    private static void requireCoherentItems(List<InspectionTemplateItemRequest> items) {
        Set<String> codes = new HashSet<>();
        for (int index = 0; index < items.size(); index++) {
            InspectionTemplateItemRequest item = items.get(index);
            if (!codes.add(item.code())) {
                throw new ValidationException("items[" + index + "].code: '" + item.code() + "' is repeated in the template");
            }
            if (item.minValue() != null && item.maxValue() != null && item.minValue().compareTo(item.maxValue()) > 0) {
                throw new ValidationException("items[" + index + "].minValue cannot be greater than maxValue");
            }
        }
    }

    private static NotFoundException notFound(UUID id) {
        return new NotFoundException("Inspection template", id);
    }
}
