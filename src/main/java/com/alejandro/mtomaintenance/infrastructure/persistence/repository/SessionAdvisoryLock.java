package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Un cerrojo de aviso de PostgreSQL de SESION, para un trabajo que no corre en una transaccion: la
 * vuelta del reintento de stock, en la que cada linea va en la suya. Un
 * {@code pg_try_advisory_xact_lock} no sirve ahi, porque se suelta al terminar la transaccion que lo
 * pidio, y uno de sesion pedido con dos llamadas a un repositorio puede caer en dos conexiones del
 * pool distintas y no soltarse nunca.
 *
 * <p>Por eso el cerrojo vive en UNA conexion pedida al {@link DataSource} fuera de toda transaccion,
 * que se queda apartada mientras dura el trabajo (el trabajo usa otras del pool) y se devuelve al
 * soltarlo. Si soltarlo falla, la conexion se aborta en vez de devolverse: volveria al pool con el
 * cerrojo puesto y ninguna otra instancia podria cogerlo hasta que la base la cerrase. Si la conexion
 * se cae a mitad, la base suelta el cerrojo sola al cerrar la sesion.</p>
 */
@Component
public class SessionAdvisoryLock {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionAdvisoryLock.class);

    private final DataSource dataSource;

    public SessionAdvisoryLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Corre {@code work} si esta instancia consigue el cerrojo {@code key}, sin esperar: {@code true}
     * si lo corrio, {@code false} si otra sesion lo tenia ya. Lo que lance el trabajo sale tal cual,
     * con el cerrojo ya suelto.
     */
    public boolean runIfFree(long key, Runnable work) {
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException unavailable) {
            throw new IllegalStateException("No connection to take the advisory lock " + key, unavailable);
        }
        boolean locked = false;
        try {
            locked = query(connection, "select pg_try_advisory_lock(?)", key);
            if (!locked) {
                return false;
            }
            work.run();
            return true;
        } catch (SQLException failed) {
            throw new IllegalStateException("Could not take the advisory lock " + key, failed);
        } finally {
            release(connection, key, locked);
        }
    }

    private static void release(Connection connection, long key, boolean locked) {
        try {
            if (locked && !query(connection, "select pg_advisory_unlock(?)", key)) {
                LOGGER.warn("The advisory lock {} was not held by this session when releasing it", key);
            }
            connection.close();
        } catch (SQLException failed) {
            LOGGER.warn("Could not release the advisory lock {}; the connection is aborted instead of returned to the pool: {}",
                    key, failed.toString());
            try {
                connection.abort(Runnable::run);
            } catch (SQLException ignored) {
                // La conexion ya estaba rota: la base solto el cerrojo al perder la sesion.
            }
        }
    }

    private static boolean query(Connection connection, String sql, long key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }
}
