package io.github.williamhuang1261.qrp.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;

/**
 * Discovers plugins of one SPI type on the classpath and indexes them by id.
 *
 * <p>This is the mechanism that makes the public build honest. Nothing in this
 * repository names the indicators or strategies it can run: they are found
 * through {@link ServiceLoader}, so a private jar on the classpath contributes
 * its own without a line of change here, and the public jar shows the contract
 * they satisfy.
 *
 * <p>Duplicate ids are an error rather than a silent override. A private
 * indicator quietly shadowing {@code sma} would mean two runs of the same
 * configuration producing different numbers, with nothing in the output saying
 * which one ran.
 */
public final class PluginRegistry<T> {

    private final Class<T> service;
    private final Map<String, T> byId;

    private PluginRegistry(Class<T> service, Map<String, T> byId) {
        this.service = service;
        this.byId = byId;
    }

    /** Loads every provider declared in {@code META-INF/services} for this type. */
    public static <T> PluginRegistry<T> load(Class<T> service, Function<? super T, String> idOf) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(idOf, "idOf");
        List<T> found = new ArrayList<>();
        ServiceLoader.load(service).forEach(found::add);
        return of(service, found, idOf);
    }

    /** Same indexing rules, over an explicit collection; used by tests. */
    public static <T> PluginRegistry<T> of(
            Class<T> service, Collection<? extends T> plugins, Function<? super T, String> idOf) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(plugins, "plugins");
        Objects.requireNonNull(idOf, "idOf");

        Map<String, T> byId = new LinkedHashMap<>();
        for (T plugin : plugins) {
            String id = idOf.apply(plugin);
            if (id == null || id.isBlank()) {
                throw new IllegalStateException(
                        plugin.getClass().getName() + " declares a blank " + service.getSimpleName() + " id");
            }
            T previous = byId.putIfAbsent(id, plugin);
            if (previous != null) {
                throw new IllegalStateException("duplicate " + service.getSimpleName() + " id '" + id
                        + "' declared by " + previous.getClass().getName()
                        + " and " + plugin.getClass().getName());
            }
        }
        // LinkedHashMap, not Map.copyOf: the copy factory salts its iteration order
        // per JVM run, and the order plugins were declared in is what the CLI and the
        // UI list them in. An unstable listing would reshuffle on every restart.
        return new PluginRegistry<>(service, Collections.unmodifiableMap(byId));
    }

    public List<T> all() {
        return List.copyOf(byId.values());
    }

    public Set<String> ids() {
        return byId.keySet();
    }

    public int size() {
        return byId.size();
    }

    public Optional<T> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** @throws IllegalArgumentException naming the ids that do exist */
    public T require(String id) {
        T plugin = byId.get(id);
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "unknown " + service.getSimpleName() + " '" + id + "'; available: " + byId.keySet());
        }
        return plugin;
    }

    @Override
    public String toString() {
        return "PluginRegistry[" + service.getSimpleName() + ": " + byId.keySet() + "]";
    }
}
