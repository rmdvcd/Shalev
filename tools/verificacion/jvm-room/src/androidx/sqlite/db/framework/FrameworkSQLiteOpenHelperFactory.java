package androidx.sqlite.db.framework;

import androidx.sqlite.db.SupportSQLiteOpenHelper;
import cu.spvi.verificacion.room.JdbcOpenHelper;

/**
 * SOLO PARA tools/verificacion (no forma parte de la app). Sustituye en el classpath a la fábrica por defecto de Room
 * (android.database.sqlite, nativa) por SQLite vía JDBC, para ejecutar en la JVM los tests de Room que en Gradle son
 * instrumentados (Android/data/src/androidTest). Room usa esta clase cuando el builder no recibe openHelperFactory.
 */
public final class FrameworkSQLiteOpenHelperFactory implements SupportSQLiteOpenHelper.Factory {
    @Override
    public SupportSQLiteOpenHelper create(SupportSQLiteOpenHelper.Configuration configuration) {
        return new JdbcOpenHelper(configuration);
    }
}
