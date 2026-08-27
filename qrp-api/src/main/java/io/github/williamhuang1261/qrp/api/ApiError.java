package io.github.williamhuang1261.qrp.api;

/** The body of a 400 response: the same message a CLI user would see. */
public record ApiError(String message) {
}
