package io.github.williamhuang1261.qrp.warehouse;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Builds a migrated {@link DataSource}: a real, external PostgreSQL when
 * {@code QRP_DB_URL} is set, otherwise a real, embedded one.
 *
 * <p>"Real" is not a euphemism in either branch. The embedded path runs the
 * actual PostgreSQL server binary published by EnterpriseDB as a managed
 * subprocess ({@link EmbeddedPostgres}) -- the SQL, the wire protocol and the
 * driver are all genuinely PostgreSQL, not a compatibility-mode substitute
 * such as H2. It needs no Docker daemon and no account, which is why it is
 * the zero-setup default here: this machine's Docker CLI is installed but its
 * daemon is not running, so a design that depended on one would have been
 * unverifiable during development and undemoable on a reviewer's laptop with
 * the same gap. Pointing at a real hosted Postgres instance is a config
 * change ({@code QRP_DB_URL}/{@code QRP_DB_USER}/{@code QRP_DB_PASSWORD}),
 * not a code change.
 */
public final class WarehouseDataSourceFactory {

    private static volatile EmbeddedPostgres embedded;

    private WarehouseDataSourceFactory() {
    }

    /** Builds (or reuses) a migrated {@link DataSource} per the rules above. */
    public static synchronized DataSource create() {
        DataSource dataSource = fromEnvironment().orElseGet(WarehouseDataSourceFactory::embeddedDataSource);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        return dataSource;
    }

    private static java.util.Optional<DataSource> fromEnvironment() {
        String url = System.getenv("QRP_DB_URL");
        if (url == null || url.isBlank()) {
            return java.util.Optional.empty();
        }
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(url);
        String user = System.getenv("QRP_DB_USER");
        if (user != null) {
            dataSource.setUser(user);
        }
        String password = System.getenv("QRP_DB_PASSWORD");
        if (password != null) {
            dataSource.setPassword(password);
        }
        return java.util.Optional.of(dataSource);
    }

    /**
     * One embedded server per JVM, started lazily on first use. Starting a
     * real Postgres process costs real time (roughly a second), so every
     * caller in the same JVM -- every repository, every test in a module --
     * shares the one instance rather than paying that cost per call.
     */
    private static DataSource embeddedDataSource() {
        EmbeddedPostgres instance = embedded;
        if (instance == null) {
            synchronized (WarehouseDataSourceFactory.class) {
                instance = embedded;
                if (instance == null) {
                    try {
                        instance = EmbeddedPostgres.builder().start();
                    } catch (IOException e) {
                        throw new UncheckedIOException("failed to start embedded postgres", e);
                    }
                    embedded = instance;
                    Runtime.getRuntime().addShutdownHook(new Thread(WarehouseDataSourceFactory::closeEmbedded));
                }
            }
        }
        return instance.getPostgresDatabase();
    }

    private static void closeEmbedded() {
        EmbeddedPostgres instance = embedded;
        if (instance != null) {
            try {
                instance.close();
            } catch (IOException ignored) {
                // best-effort on JVM shutdown; nothing meaningful to do with the failure here
            }
        }
    }
}
