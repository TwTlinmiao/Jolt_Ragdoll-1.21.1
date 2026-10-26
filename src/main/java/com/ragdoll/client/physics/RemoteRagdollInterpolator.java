package com.ragdoll.client.physics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Buffers timestamped server snapshots and samples them on a slightly delayed
 * timeline so uneven packet arrival does not become visible movement jitter.
 */
final class RemoteRagdollInterpolator {
    private static final int MAX_SNAPSHOTS = 8;
    private static final int CLOCK_SAMPLE_COUNT = 8;
    private static final long MAX_PACKET_INTERVAL_NANOS = 1_000_000_000L;
    private static final long MIN_DELAY_NANOS = 50_000_000L;
    private static final long MAX_DELAY_NANOS = 120_000_000L;
    private static final long DEFAULT_DELAY_NANOS = 100_000_000L;
    private static final long MAX_EXTRAPOLATION_NANOS = 150_000_000L;
    private static final double DELAY_EMA_ALPHA = 0.15;
    private static final double DELAY_SAFETY_FACTOR = 1.5;
    private static final double VELOCITY_THRESHOLD_SQUARED = 0.0001;

    private final Deque<TimedState> snapshots = new ArrayDeque<>(MAX_SNAPSHOTS);
    private final List<Long> clockOffsetSamples = new ArrayList<>(CLOCK_SAMPLE_COUNT);

    private long lastSequence = Long.MIN_VALUE;
    private long lastServerTimestamp = Long.MIN_VALUE;
    private long lastArrivalNanos;
    private long clockOffsetNanos;
    private long adaptiveDelayNanos = DEFAULT_DELAY_NANOS;
    private boolean clockInitialized;
    private double smoothedIntervalNanos;
    private double smoothedJitterNanos;

    void add(
            long serverTimestampNanos,
            long sequence,
            RagdollPhysicsState state,
            long arrivalNanos) {
        if (state == null
                || sequence <= lastSequence
                || serverTimestampNanos <= lastServerTimestamp) {
            return;
        }

        updatePacketTiming(arrivalNanos);
        updateClockOffset(serverTimestampNanos - arrivalNanos);

        snapshots.addLast(new TimedState(serverTimestampNanos, state));
        while (snapshots.size() > MAX_SNAPSHOTS) {
            snapshots.removeFirst();
        }
        lastSequence = sequence;
        lastServerTimestamp = serverTimestampNanos;
    }

    RagdollPhysicsState sample(long clientNowNanos) {
        TimedState latest = snapshots.peekLast();
        if (latest == null) {
            return null;
        }
        if (snapshots.size() == 1 || !clockInitialized) {
            return latest.state;
        }

        long renderTimestamp =
                clientNowNanos + clockOffsetNanos - adaptiveDelayNanos;
        TimedState first = snapshots.peekFirst();
        if (renderTimestamp <= first.timestampNanos) {
            return first.state;
        }

        TimedState previous = first;
        for (TimedState current : snapshots) {
            if (current == first) {
                continue;
            }
            if (renderTimestamp <= current.timestampNanos) {
                double duration =
                        current.timestampNanos - previous.timestampNanos;
                double alpha = duration <= 0.0
                        ? 1.0
                        : (renderTimestamp - previous.timestampNanos) / duration;
                return interpolate(previous.state, current.state, alpha);
            }
            previous = current;
        }

        return extrapolate(
                latest.state, renderTimestamp - latest.timestampNanos);
    }

    private void updatePacketTiming(long arrivalNanos) {
        if (lastArrivalNanos != 0L) {
            long interval = arrivalNanos - lastArrivalNanos;
            if (interval > 0L && interval < MAX_PACKET_INTERVAL_NANOS) {
                if (smoothedIntervalNanos == 0.0) {
                    smoothedIntervalNanos = interval;
                    smoothedJitterNanos = interval * 0.25;
                } else {
                    double deviation =
                            Math.abs(interval - smoothedIntervalNanos);
                    smoothedJitterNanos =
                            smoothedJitterNanos * (1.0 - DELAY_EMA_ALPHA)
                                    + deviation * DELAY_EMA_ALPHA;
                    smoothedIntervalNanos =
                            smoothedIntervalNanos * (1.0 - DELAY_EMA_ALPHA)
                                    + interval * DELAY_EMA_ALPHA;
                }
                long calculated = (long) (
                        smoothedIntervalNanos * DELAY_SAFETY_FACTOR
                                + smoothedJitterNanos * 2.0);
                calculated = Math.clamp(
                        calculated, MIN_DELAY_NANOS, MAX_DELAY_NANOS);
                adaptiveDelayNanos = (long) (
                        adaptiveDelayNanos * (1.0 - DELAY_EMA_ALPHA)
                                + calculated * DELAY_EMA_ALPHA);
            }
        }
        lastArrivalNanos = arrivalNanos;
    }

    private void updateClockOffset(long sample) {
        if (!clockInitialized) {
            clockOffsetNanos = sample;
            clockInitialized = true;
        }
        clockOffsetSamples.add(sample);
        if (clockOffsetSamples.size() < CLOCK_SAMPLE_COUNT) {
            return;
        }

        Collections.sort(clockOffsetSamples);
        int trim = clockOffsetSamples.size() / 4;
        long sum = 0L;
        int count = 0;
        for (int i = trim; i < clockOffsetSamples.size() - trim; ++i) {
            sum += clockOffsetSamples.get(i);
            ++count;
        }
        if (count > 0) {
            long average = sum / count;
            clockOffsetNanos =
                    (long) (clockOffsetNanos * 0.8 + average * 0.2);
        }
        clockOffsetSamples.clear();
    }

    private static RagdollPhysicsState interpolate(
            RagdollPhysicsState from,
            RagdollPhysicsState to,
            double alpha) {
        alpha = Math.clamp(alpha, 0.0, 1.0);
        if (from.parts().size() != to.parts().size()) {
            return to;
        }

        List<RagdollPhysicsState.PartState> result =
                new ArrayList<>(to.parts().size());
        for (RagdollPhysicsState.PartState toPart : to.parts()) {
            RagdollPhysicsState.PartState fromPart =
                    findPart(from, toPart.partName());
            if (fromPart == null) {
                return to;
            }
            float[] rotation = slerp(fromPart, toPart, (float) alpha);
            result.add(new RagdollPhysicsState.PartState(
                    toPart.partName(),
                    lerp(fromPart.x(), toPart.x(), alpha),
                    lerp(fromPart.y(), toPart.y(), alpha),
                    lerp(fromPart.z(), toPart.z(), alpha),
                    rotation[0],
                    rotation[1],
                    rotation[2],
                    rotation[3],
                    (float) lerp(fromPart.linearX(), toPart.linearX(), alpha),
                    (float) lerp(fromPart.linearY(), toPart.linearY(), alpha),
                    (float) lerp(fromPart.linearZ(), toPart.linearZ(), alpha),
                    (float) lerp(fromPart.angularX(), toPart.angularX(), alpha),
                    (float) lerp(fromPart.angularY(), toPart.angularY(), alpha),
                    (float) lerp(fromPart.angularZ(), toPart.angularZ(), alpha),
                    toPart.frozen()));
        }
        return new RagdollPhysicsState(result);
    }

    private static RagdollPhysicsState extrapolate(
            RagdollPhysicsState latest,
            long extrapolationNanos) {
        if (extrapolationNanos <= 0L) {
            return latest;
        }

        long clampedNanos =
                Math.min(extrapolationNanos, MAX_EXTRAPOLATION_NANOS);
        double seconds = clampedNanos / 1_000_000_000.0;
        double decay =
                1.0 - 0.5 * clampedNanos / MAX_EXTRAPOLATION_NANOS;
        double displacementTime = seconds * decay;
        List<RagdollPhysicsState.PartState> result =
                new ArrayList<>(latest.parts().size());

        for (RagdollPhysicsState.PartState part : latest.parts()) {
            double velocitySquared =
                    part.linearX() * part.linearX()
                            + part.linearY() * part.linearY()
                            + part.linearZ() * part.linearZ();
            double movementTime = velocitySquared > VELOCITY_THRESHOLD_SQUARED
                    ? displacementTime
                    : 0.0;
            result.add(new RagdollPhysicsState.PartState(
                    part.partName(),
                    part.x() + part.linearX() * movementTime,
                    part.y() + part.linearY() * movementTime,
                    part.z() + part.linearZ() * movementTime,
                    part.rotationX(),
                    part.rotationY(),
                    part.rotationZ(),
                    part.rotationW(),
                    part.linearX(),
                    part.linearY(),
                    part.linearZ(),
                    part.angularX(),
                    part.angularY(),
                    part.angularZ(),
                    part.frozen()));
        }
        return new RagdollPhysicsState(result);
    }

    private static RagdollPhysicsState.PartState findPart(
            RagdollPhysicsState state, String name) {
        for (RagdollPhysicsState.PartState part : state.parts()) {
            if (part.partName().equals(name)) {
                return part;
            }
        }
        return null;
    }

    private static double lerp(double from, double to, double alpha) {
        return from + (to - from) * alpha;
    }

    private static float[] slerp(
            RagdollPhysicsState.PartState from,
            RagdollPhysicsState.PartState to,
            float alpha) {
        float ax = from.rotationX();
        float ay = from.rotationY();
        float az = from.rotationZ();
        float aw = from.rotationW();
        float bx = to.rotationX();
        float by = to.rotationY();
        float bz = to.rotationZ();
        float bw = to.rotationW();

        float dot = ax * bx + ay * by + az * bz + aw * bw;
        if (dot < 0.0f) {
            dot = -dot;
            bx = -bx;
            by = -by;
            bz = -bz;
            bw = -bw;
        }
        dot = Math.clamp(dot, -1.0f, 1.0f);

        float scaleFrom;
        float scaleTo;
        if (dot > 0.9995f) {
            scaleFrom = 1.0f - alpha;
            scaleTo = alpha;
        } else {
            double theta = Math.acos(dot);
            double inverseSin = 1.0 / Math.sin(theta);
            scaleFrom = (float) (
                    Math.sin((1.0f - alpha) * theta) * inverseSin);
            scaleTo = (float) (Math.sin(alpha * theta) * inverseSin);
        }

        float x = scaleFrom * ax + scaleTo * bx;
        float y = scaleFrom * ay + scaleTo * by;
        float z = scaleFrom * az + scaleTo * bz;
        float w = scaleFrom * aw + scaleTo * bw;
        float inverseLength =
                1.0f / (float) Math.sqrt(x * x + y * y + z * z + w * w);
        return new float[] {
                x * inverseLength,
                y * inverseLength,
                z * inverseLength,
                w * inverseLength
        };
    }

    private record TimedState(
            long timestampNanos, RagdollPhysicsState state) {
    }
}
