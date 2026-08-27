package io.github.williamhuang1261.qrp.report;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Optional narrative backed by a local Ollama server, using only the JDK's
 * {@link HttpClient} -- no new dependency for something that is off by
 * default.
 *
 * <p>Same honest-fallback pattern the plan borrows from {@code mortality-copilot}'s
 * RAG copilot: this is opt-in, tries the local endpoint on a short timeout,
 * and on any failure -- server not running, connection refused, timeout, a
 * malformed or empty response -- falls back to {@link TemplateNarrativeGenerator}'s
 * output rather than throwing. Whichever text is actually returned is
 * labelled with which generator produced it, never silently either way.
 */
public final class OllamaNarrativeGenerator implements NarrativeGenerator {

    public static final String LABEL_PREFIX = "[AI-generated summary] ";

    private static final String DEFAULT_ENDPOINT = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "llama3.2";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    private final URI endpoint;
    private final String model;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final NarrativeGenerator fallback;

    public OllamaNarrativeGenerator() {
        this(URI.create(DEFAULT_ENDPOINT), DEFAULT_MODEL, DEFAULT_TIMEOUT);
    }

    public OllamaNarrativeGenerator(URI endpoint, String model, Duration timeout) {
        this.endpoint = endpoint;
        this.model = model;
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        this.fallback = new TemplateNarrativeGenerator();
    }

    @Override
    public String narrate(FundComparisonTable table) {
        Optional<String> generated = tryGenerate(table);
        return generated.map(text -> LABEL_PREFIX + text).orElseGet(() -> fallback.narrate(table));
    }

    private Optional<String> tryGenerate(FundComparisonTable table) {
        try {
            String prompt = promptFor(table);
            String requestBody = String.format(Locale.ROOT,
                    "{\"model\":%s,\"prompt\":%s,\"stream\":false}",
                    jsonString(model), jsonString(prompt));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(endpoint.resolve("/api/generate"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            String text = extractResponseField(response.body());
            return (text == null || text.isBlank()) ? Optional.empty() : Optional.of(text.trim());
        } catch (IOException | InterruptedException | RuntimeException e) {
            // Server not running, connection refused, timeout, malformed JSON -- all fail closed
            // to the template. An interrupted thread's status is not restored here on purpose:
            // this call is not cancellable work, it is a best-effort optional enhancement, and
            // restoring interrupt status would only make the caller's own bookkeeping look like
            // the caller was interrupted when it was in fact this narrator that was.
            return Optional.empty();
        }
    }

    private static String promptFor(FundComparisonTable table) {
        String rows = table.rows().stream()
                .map(row -> String.format(Locale.ROOT,
                        "%s%s: net CAGR %.2f%%, Sharpe %.2f, max drawdown %.2f%%, vs. benchmark %.0f bps",
                        row.displayName(), row.isBenchmark() ? " (benchmark)" : "",
                        row.netCagr() * 100.0, row.sharpeRatio(), row.maxDrawdown() * 100.0,
                        row.benchmarkRelativeBps()))
                .collect(Collectors.joining("\n"));
        return "Write a short, plain-English paragraph (3-4 sentences) for a fund comparison "
                + "report aimed at a non-technical reader. State which fund had the best net "
                + "return and which had the best risk-adjusted (Sharpe) return, and how the "
                + "leading fund compared to the benchmark. Do not invent numbers beyond those "
                + "given.\n\n" + rows;
    }

    /** Ollama's {@code /api/generate} response is a single JSON object with a top-level "response" string. */
    private static String extractResponseField(String body) {
        if (body == null) {
            return null;
        }
        int key = body.indexOf("\"response\"");
        if (key < 0) {
            return null;
        }
        int colon = body.indexOf(':', key);
        if (colon < 0) {
            return null;
        }
        int start = body.indexOf('"', colon + 1);
        if (start < 0) {
            return null;
        }
        StringBuilder value = new StringBuilder();
        boolean escape = false;
        for (int i = start + 1; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escape) {
                switch (c) {
                    case 'n' -> value.append('\n');
                    case 't' -> value.append('\t');
                    case '"' -> value.append('"');
                    case '\\' -> value.append('\\');
                    default -> value.append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                return value.toString();
            } else {
                value.append(c);
            }
        }
        return null;
    }

    private static String jsonString(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
