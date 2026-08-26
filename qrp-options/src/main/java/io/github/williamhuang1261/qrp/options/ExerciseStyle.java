package io.github.williamhuang1261.qrp.options;

/**
 * When the holder may exercise.
 *
 * <p>Only these two ship. Bermudan needs an exercise calendar, which is a venue
 * detail this platform deliberately does not model.
 */
public enum ExerciseStyle {
    /** Exercisable at expiry only. Closed form applies. */
    EUROPEAN,

    /** Exercisable at any time up to expiry. Needs a lattice or a free boundary. */
    AMERICAN
}
