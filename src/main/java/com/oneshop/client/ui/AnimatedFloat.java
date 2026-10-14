package com.oneshop.client.ui;

import io.wispforest.owo.ui.core.Animatable;
import io.wispforest.owo.ui.core.AnimatableProperty;
import io.wispforest.owo.ui.core.Easing;

/**
 * Normalized float driven by an owo-ui {@link AnimatableProperty}.
 * <p>
 * owo's {@code AnimatableProperty} only accepts {@link Animatable} values, so the float is wrapped in a tiny record.
 * The property is advanced lazily from wall-clock time on every {@link #get()}, which makes the value usable from
 * {@code Surface} lambdas and other places that are not part of owo's component update loop.
 * <p>
 * Re-targeting is clean: {@link AnimatableProperty#animate} always starts from the current value and replaces the
 * previous animation, so rapid interactions redirect smoothly instead of snapping or desyncing.
 */
public final class AnimatedFloat {
    private static final float NANOS_PER_TICK = 50_000_000f;

    private record Value(float v) implements Animatable<Value> {
        @Override
        public Value interpolate(Value next, float delta) {
            return new Value(this.v + (next.v - this.v) * delta);
        }
    }

    private final AnimatableProperty<Value> property;
    private float target;
    private long lastNanos;
    private Runnable onCompleteCallback;
    private boolean completionInvoked = true;

    public AnimatedFloat(float initial) {
        this.property = AnimatableProperty.of(new Value(initial));
        this.target = initial;
        this.lastNanos = System.nanoTime();
    }

    /** Smoothly moves to {@code to}; no-op if already heading there. */
    public AnimatedFloat animateTo(float to, int durationMs, Easing easing) {
        if (to == this.target) {
            return this;
        }
        this.target = to;
        this.lastNanos = System.nanoTime();
        this.completionInvoked = false;

        if (durationMs <= 0 || Math.abs(this.property.get().v() - to) < 1e-4f) {
            snapTo(to);
            notifyCompletion();
            return this;
        }

        var anim = this.property.animate(durationMs, easing, new Value(to));
        anim.finished().subscribe((dir, looping) -> notifyCompletion());
        anim.forwards();
        return this;
    }

    public AnimatedFloat animateTo(float to, int durationMs, Easing easing, Runnable onComplete) {
        this.onCompleteCallback = onComplete;
        return animateTo(to, durationMs, easing);
    }

    public AnimatedFloat onComplete(Runnable onComplete) {
        this.onCompleteCallback = onComplete;
        if (!this.completionInvoked && hasReachedTarget()) {
            notifyCompletion();
        }
        return this;
    }

    private boolean hasReachedTarget() {
        return Math.abs(this.property.get().v() - this.target) < 1e-4f;
    }

    private void notifyCompletion() {
        if (!this.completionInvoked) {
            this.completionInvoked = true;
            if (this.onCompleteCallback != null) {
                Runnable cb = this.onCompleteCallback;
                this.onCompleteCallback = null;
                cb.run();
            }
        }
    }

    /** Jumps to {@code value} immediately, cancelling any running animation. */
    public void snapTo(float value) {
        this.target = value;
        this.completionInvoked = true;
        // A freshly created animation is paused (BACKWARDS at delta 0), so it acts as a clean "cancel".
        this.property.animate(1, Easing.LINEAR, new Value(value));
        this.property.set(new Value(value));
    }

    public float target() {
        return this.target;
    }

    /** Advances the animation by the elapsed real time and returns the current value. */
    public float get() {
        if (this.completionInvoked) {
            return this.target;
        }
        long now = System.nanoTime();
        long elapsedNanos = now - this.lastNanos;
        this.lastNanos = now;
        if (elapsedNanos > 0) {
            float ticks = Math.min((float) elapsedNanos / NANOS_PER_TICK, 2f);
            this.property.update(ticks);
        }
        if (!this.completionInvoked && hasReachedTarget()) {
            notifyCompletion();
        }
        return this.property.get().v();
    }

    public static int lerpArgb(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = lerpChannel(from >>> 24, to >>> 24, t);
        int r = lerpChannel((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerpChannel((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerpChannel(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Multiplies the alpha channel of {@code argb} by {@code factor}. */
    public static int scaleAlpha(int argb, float factor) {
        int a = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, factor)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int lerpChannel(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }
}
