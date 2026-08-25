package io.github.williamhuang1261.qrp.nativeengine;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Finds, loads and binds the compute kernel, or explains why it could not.
 *
 * <p>Loading is attempted once, and every failure is captured as a message
 * rather than thrown. A missing kernel is a supported state of the system: the
 * platform runs on the Java engine, and the reason the fast path is not in use
 * has to be answerable without a stack trace.
 *
 * <p>The ABI version is checked on load. A stale library whose signatures no
 * longer match would otherwise be loaded successfully and produce numbers that
 * disagree with Java, which is the worst possible failure mode for a component
 * whose entire justification is that it agrees.
 */
final class KernelLibrary {

    private static final int EXPECTED_ABI_VERSION = 1;
    private static final String PROPERTY = "qrp.native.library";
    private static final String ENVIRONMENT = "QRP_NATIVE_LIBRARY";

    private static final KernelLibrary INSTANCE = load();

    private final MethodHandle rollingMean;
    private final MethodHandle bootstrapMeans;
    private final MethodHandle openmpThreads;
    private final Path path;
    private final String unavailableReason;

    private KernelLibrary(MethodHandle rollingMean, MethodHandle bootstrapMeans,
            MethodHandle openmpThreads, Path path, String unavailableReason) {
        this.rollingMean = rollingMean;
        this.bootstrapMeans = bootstrapMeans;
        this.openmpThreads = openmpThreads;
        this.path = path;
        this.unavailableReason = unavailableReason;
    }

    static KernelLibrary instance() {
        return INSTANCE;
    }

    boolean isAvailable() {
        return unavailableReason == null;
    }

    /** Why the kernel is not in use, or null when it is. */
    String unavailableReason() {
        return unavailableReason;
    }

    Optional<Path> path() {
        return Optional.ofNullable(path);
    }

    int openmpThreads() {
        requireAvailable();
        try {
            return (int) openmpThreads.invokeExact();
        } catch (Throwable e) {
            throw new IllegalStateException("qrp_openmp_threads failed", e);
        }
    }

    double[] rollingMean(double[] values, int window) {
        requireAvailable();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment in = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, values);
            MemorySegment out = arena.allocate(ValueLayout.JAVA_DOUBLE, values.length);
            rollingMean.invokeExact(in, values.length, window, out);
            return out.toArray(ValueLayout.JAVA_DOUBLE);
        } catch (Throwable e) {
            throw new IllegalStateException("qrp_rolling_mean failed", e);
        }
    }

    double[] bootstrapMeans(double[] sample, int draws, int blockSize, long seed) {
        requireAvailable();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment in = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, sample);
            MemorySegment out = arena.allocate(ValueLayout.JAVA_DOUBLE, draws);
            bootstrapMeans.invokeExact(in, sample.length, draws, blockSize, seed, out);
            return out.toArray(ValueLayout.JAVA_DOUBLE);
        } catch (Throwable e) {
            throw new IllegalStateException("qrp_bootstrap_means failed", e);
        }
    }

    private void requireAvailable() {
        if (!isAvailable()) {
            throw new IllegalStateException("native kernel is unavailable: " + unavailableReason);
        }
    }

    private static KernelLibrary load() {
        Optional<Path> found = locate();
        if (found.isEmpty()) {
            return unavailable("no " + libraryFileName() + " found; searched "
                    + searchPaths() + " (build it with: make -C native)");
        }

        Path library = found.get();
        try {
            // Shared, not confined: the handles outlive this method and are used
            // for the life of the JVM.
            Arena arena = Arena.ofShared();
            SymbolLookup lookup = SymbolLookup.libraryLookup(library, arena);
            Linker linker = Linker.nativeLinker();

            MethodHandle abiVersion = linker.downcallHandle(
                    lookup.find("qrp_abi_version").orElseThrow(
                            () -> new IllegalStateException("qrp_abi_version is missing")),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT));
            int version = (int) abiVersion.invokeExact();
            if (version != EXPECTED_ABI_VERSION) {
                return unavailable(library + " reports ABI version " + version
                        + ", expected " + EXPECTED_ABI_VERSION + "; rebuild with: make -C native clean all");
            }

            MethodHandle threads = linker.downcallHandle(
                    lookup.find("qrp_openmp_threads").orElseThrow(
                            () -> new IllegalStateException("qrp_openmp_threads is missing")),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT));

            MethodHandle rolling = linker.downcallHandle(
                    lookup.find("qrp_rolling_mean").orElseThrow(
                            () -> new IllegalStateException("qrp_rolling_mean is missing")),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

            MethodHandle bootstrap = linker.downcallHandle(
                    lookup.find("qrp_bootstrap_means").orElseThrow(
                            () -> new IllegalStateException("qrp_bootstrap_means is missing")),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
                            ValueLayout.ADDRESS));

            return new KernelLibrary(rolling, bootstrap, threads, library, null);
        } catch (Throwable e) {
            return unavailable("could not bind " + library + ": " + e);
        }
    }

    private static KernelLibrary unavailable(String reason) {
        return new KernelLibrary(null, null, null, null, reason);
    }

    private static Optional<Path> locate() {
        String configured = System.getProperty(PROPERTY, System.getenv(ENVIRONMENT));
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured);
            return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
        }
        return searchPaths().stream().filter(Files::isRegularFile).findFirst();
    }

    /**
     * Where to look, in order.
     *
     * <p>The working directory alone is not enough: a launcher that forks its own
     * JVM decides that directory, and the kernel then appears to be missing
     * depending on how the application was started. The last entries are relative
     * to <em>this class's own location</em>, which is fixed regardless of who
     * launched it.
     */
    private static List<Path> searchPaths() {
        String file = libraryFileName();
        List<Path> paths = new java.util.ArrayList<>(List.of(
                Path.of("native", "build", file),
                Path.of("..", "native", "build", file),
                Path.of("..", "..", "native", "build", file)));
        codeSourceDirectory().ifPresent(directory -> {
            // .../qrp-native/target/classes or .../qrp-native-x.y.z.jar
            for (int up = 1; up <= 4; up++) {
                Path candidate = directory;
                for (int step = 0; step < up && candidate != null; step++) {
                    candidate = candidate.getParent();
                }
                if (candidate != null) {
                    paths.add(candidate.resolve("native").resolve("build").resolve(file));
                }
            }
        });
        return List.copyOf(paths);
    }

    private static Optional<Path> codeSourceDirectory() {
        try {
            var source = KernelLibrary.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return Optional.empty();
            }
            Path location = Path.of(source.getLocation().toURI());
            return Optional.ofNullable(Files.isDirectory(location) ? location : location.getParent());
        } catch (RuntimeException | java.net.URISyntaxException e) {
            return Optional.empty();
        }
    }

    private static String libraryFileName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String extension = os.contains("mac") || os.contains("darwin") ? "dylib"
                : os.contains("win") ? "dll" : "so";
        return "libqrpkernel." + extension;
    }
}
