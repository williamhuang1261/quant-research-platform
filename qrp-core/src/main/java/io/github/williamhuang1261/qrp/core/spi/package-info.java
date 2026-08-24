/**
 * The four extension points of the platform.
 *
 * <p>Every algorithm in this repository is a plugin behind one of these
 * interfaces, discovered at runtime through {@link java.util.ServiceLoader}.
 * That is what lets the public repository ship textbook reference
 * implementations while a private jar on the classpath contributes its own,
 * with no change to a single line here.
 */
package io.github.williamhuang1261.qrp.core.spi;
