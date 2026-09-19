package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetSwitch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Agujas de un aislador de sección.
 *
 * <p>Las escribe el manejador de datos maestros, nunca la API: el aislador y sus agujas los posee
 * {@code mto-configuration} y aquí sólo se guarda el snapshot.
 */
@Repository
public interface CatenaryAssetSwitchRepository extends JpaRepository<CatenaryAssetSwitch, UUID> {

    List<CatenaryAssetSwitch> findByAssetIdOrderByKpAscCodeAsc(UUID assetId);

    /**
     * Vacía el bloque de agujas del activo.
     *
     * <p>Borrar y reinsertar, y no reconciliar una por una: el emisor manda siempre la lista
     * completa —es su contrato—, así que lo que llega es el estado final. Reconciliar añadiría
     * código y una lectura para llegar exactamente al mismo sitio, y las agujas no tienen aquí
     * historial que conservar (ver {@code CatenaryAssetSwitch}: no se auditan).
     *
     * <p>{@code flushAutomatically} y <b>no</b> {@code clearAutomatically}: un borrado masivo en
     * JPQL va directo a la base de datos, así que hay que vaciar antes lo que esté pendiente o una
     * inserción encolada sobreviviría al borrado. Limpiar el contexto, en cambio, desataría el
     * activo que el manejador acaba de leer y va a usar como padre de las agujas nuevas.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from CatenaryAssetSwitch s where s.asset.id = :assetId")
    int deleteByAssetId(@Param("assetId") UUID assetId);
}
