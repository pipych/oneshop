package com.oneshop.client.ui;

import io.wispforest.owo.ui.core.Animatable;
import io.wispforest.owo.ui.core.Easing;

/**
 * Type-safe animatable numeric property supporting {@link Float} and {@link Integer}.
 * Backed by high-precision wall-clock interpolation and owo-lib easing.
 */
public final class AnimatableProperty<T extends Number> {
    private static final float NANOS_PER_TICK = 50_000_000f; // 20 ticks/sec

    private record NumberValue(double v) implements Animatable<NumberValue> {
        @Override
        public NumberValue interpolate(NumberValue next, float delta) {
            return new NumberValue(this.v + (next.v - this.v) * delta);
        }
    }

    private final io.wispforest.owo.ui.core.AnimatableProperty<NumberValue> inner;
    private final Class<T> clazz;
    private double target;
    private T cachedTargetBoxed;
    private long lastNanos;
    private Runnable onCompleteCallback;
    private boolean completionInvoked = true;

    private AnimatableProperty(Class<T> clazz, double initial) {
        this.clazz = clazz;
        this.target = initial;
        this.cachedTargetBoxed = cast(initial);
        this.inner = io.wispforest.owo.ui.core.AnimatableProperty.of(new NumberValue(initial));
        this.lastNanos = System.nanoTime();
    }

    public static AnimatableProperty<Float> of(Float initial) {
        return new AnimatableProperty<>(Float.class, initial != null ? initial : 0.0f);
    }

    public static AnimatableProperty<Integer> of(Integer initial) {
        return new AnimatableProperty<>(Integer.class, initial != null ? initial : 0);
    }

    public static <N extends Number> AnimatableProperty<N> ofNumber(Class<N> clazz, N initial) {
        return new AnimatableProperty<>(clazz, initial != null ? initial.doubleValue() : 0.0);
    }

    public AnimatableProperty<T> animate(int durationMs, Easing easing, T targetValue) {
        double t = targetValue != null ? targetValue.doubleValue() : 0.0;
        this.target = t;
        this.cachedTargetBoxed = targetValue != null ? targetValue : cast(0.0);
        this.lastNanos = System.nanoTime();
        this.completionInvoked = false;

        if (durationMs <= 0 || Math.abs(this.inner.get().v() - t) < 1e-4) {
            snapTo(targetValue);
            notifyCompletion();
            return this;
        }

        var anim = this.inner.animate(durationMs, easing, new NumberValue(t));
        anim.finished().subscribe((dir, looping) -> {
            notifyCompletion();
        });
        anim.forwards();
        return this;
    }

    public AnimatableProperty<T> animate(int durationMs, Easing easing, T targetValue, Runnable onComplete) {
        this.onCompleteCallback = onComplete;
        return animate(durationMs, easing, targetValue);
    }

    public AnimatableProperty<T> onComplete(Runnable onComplete) {
        this.onCompleteCallback = onComplete;
        if (!this.completionInvoked && hasReachedTarget()) {
            notifyCompletion();
        }
        return this;
    }

    private boolean hasReachedTarget() {
        return Math.abs(this.inner.get().v() - this.target) < 1e-4;
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

    public void snapTo(T value) {
        double v = value != null ? value.doubleValue() : 0.0;
        this.target = v;
        this.cachedTargetBoxed = value != null ? value : cast(0.0);
        this.completionInvoked = true;
        this.inner.animate(1, Easing.LINEAR, new NumberValue(v));
        this.inner.set(new NumberValue(v));
    }

    public void set(T value) {
        snapTo(value);
    }

    public T target() {
        return this.cachedTargetBoxed;
    }

    public T get() {
        if (this.completionInvoked) {
            return this.cachedTargetBoxed;
        }
        long now = System.nanoTime();
        long elapsedNanos = now - this.lastNanos;
        this.lastNanos = now;
        if (elapsedNanos > 0) {
            float ticks = Math.min((float) elapsedNanos / NANOS_PER_TICK, 2f);
            this.inner.update(ticks);
        }
        if (!this.completionInvoked && hasReachedTarget()) {
            notifyCompletion();
        }
        return cast(this.inner.get().v());
    }

    public void update(float delta) {
        this.inner.update(delta);
        if (!this.completionInvoked && hasReachedTarget()) {
            notifyCompletion();
        }
    }

    @SuppressWarnings("unchecked")
    private T cast(double val) {
        if (clazz == Float.class) {
            return (T) Float.valueOf((float) val);
        } else if (clazz == Integer.class) {
            return (T) Integer.valueOf((int) Math.round(val));
        } else if (clazz == Double.class) {
            return (T) Double.valueOf(val);
        } else if (clazz == Long.class) {
            return (T) Long.valueOf(Math.round(val));
        }
        return (T) Double.valueOf(val);
    }
}
