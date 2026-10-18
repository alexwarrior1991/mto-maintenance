package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceShiftRepository extends JpaRepository<MaintenanceShift, UUID>, JpaSpecificationExecutor<MaintenanceShift> {

    Optional<MaintenanceShift> findByCode(String code);

    List<MaintenanceShift> findByShiftDateBetweenOrderByShiftDateAsc(LocalDate from, LocalDate to);

    /**
     * Turnos abiertos de ese dia que llevan alguno de esos vehiculos, salvo el propio: un vehiculo no
     * esta en dos turnos de la misma noche. Las ventanas son nulas a menudo, asi que se compara la fecha.
     */
    @Query("""
            select distinct s.code from MaintenanceShift s join s.vehicles v
             where v.vehicle.id in :vehicleIds and s.shiftDate = :shiftDate and s.status in :statuses and s.id <> :shiftId
            """)
    List<String> findOpenShiftCodesWithVehicles(@Param("vehicleIds") Collection<UUID> vehicleIds, @Param("shiftDate") LocalDate shiftDate,
                                                @Param("statuses") Collection<ShiftStatus> statuses, @Param("shiftId") UUID shiftId);

    /** Lo mismo con las personas que van en el vehiculo: el jefe y los linemen (un supervisor lleva varios turnos). */
    @Query("""
            select distinct s.code from MaintenanceShift s left join s.teamLeader leader left join s.linemen lineman
             where (leader.id in :personIds or lineman.id in :personIds)
               and s.shiftDate = :shiftDate and s.status in :statuses and s.id <> :shiftId
            """)
    List<String> findOpenShiftCodesWithCrew(@Param("personIds") Collection<UUID> personIds, @Param("shiftDate") LocalDate shiftDate,
                                            @Param("statuses") Collection<ShiftStatus> statuses, @Param("shiftId") UUID shiftId);

    /** Lo mismo con los instrumentos: una pertiga o un detector van en un solo turno por noche. */
    @Query("""
            select distinct s.code from MaintenanceShift s join s.instruments instrument
             where instrument.id in :instrumentIds and s.shiftDate = :shiftDate and s.status in :statuses and s.id <> :shiftId
            """)
    List<String> findOpenShiftCodesWithInstruments(@Param("instrumentIds") Collection<UUID> instrumentIds,
                                                   @Param("shiftDate") LocalDate shiftDate,
                                                   @Param("statuses") Collection<ShiftStatus> statuses, @Param("shiftId") UUID shiftId);

    /**
     * Bloqueo asesor hasta el final de la transaccion, por vehiculo, persona o instrumento: dos turnos de
     * la misma noche que se los llevan a la vez se ponen en fila y el segundo ve al primero.
     * {@code pg_advisory_xact_lock} devuelve {@code void}; por eso se envuelve.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:key)) as locked", nativeQuery = true)
    int lockResource(@Param("key") long key);
}
