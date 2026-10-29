package com.web.project.common.id;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Generates positive, time-ordered IDs using a Snowflake-style bit layout.
 */
public final class SnowflakeIdGenerator {

    private static final long CUSTOM_EPOCH_MILLIS = 1767225600000L;
    private static final long SEQUENCE_MASK = (1L << 12) - 1;
    private static final long WORKER_ID_MASK = (1L << 5) - 1;
    private static final long DATACENTER_ID_MASK = (1L << 5) - 1;
    private static final int WORKER_ID_SHIFT = 12;
    private static final int DATACENTER_ID_SHIFT = 17;
    private static final int TIMESTAMP_SHIFT = 22;
    private static final String CLOCK_ROLLBACK_MESSAGE = "系统时钟发生回拨，无法安全生成 Snowflake ID";

    private final long datacenterId;
    private final long workerId;
    private final LongSupplier currentTimeMillis;

    private long lastTimestamp = -1L;
    private long sequence;

    public SnowflakeIdGenerator(long datacenterId, long workerId) {
        this(datacenterId, workerId, System::currentTimeMillis);
    }

    public SnowflakeIdGenerator(
            long datacenterId,
            long workerId,
            LongSupplier currentTimeMillis
    ) {
        validateNodeId(datacenterId, "datacenterId");
        validateNodeId(workerId, "workerId");
        this.datacenterId = datacenterId;
        this.workerId = workerId;
        this.currentTimeMillis = Objects.requireNonNull(currentTimeMillis, "currentTimeMillis");
    }

    /**
     * Returns the next unique ID for this worker.
     *
     * @throws IllegalStateException when the clock moves backwards or before the custom epoch
     */
    public synchronized long nextId() {
        long timestamp = currentTimeMillis.getAsLong();
        if (timestamp < CUSTOM_EPOCH_MILLIS) {
            throw new IllegalStateException("当前时间早于 Snowflake ID 起始时间");
        }
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException(CLOCK_ROLLBACK_MESSAGE);
        }

        long nextSequence = 0;
        if (timestamp == lastTimestamp) {
            nextSequence = (sequence + 1) & SEQUENCE_MASK;
            if (nextSequence == 0) {
                timestamp = waitUntilNextMillis(lastTimestamp);
            }
        }

        sequence = nextSequence;
        lastTimestamp = timestamp;
        return ((timestamp - CUSTOM_EPOCH_MILLIS) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long waitUntilNextMillis(long previousTimestamp) {
        long timestamp;
        do {
            timestamp = currentTimeMillis.getAsLong();
            if (timestamp < previousTimestamp) {
                throw new IllegalStateException(CLOCK_ROLLBACK_MESSAGE);
            }
        } while (timestamp <= previousTimestamp);
        return timestamp;
    }

    private static void validateNodeId(long value, String name) {
        long max = name.equals("workerId") ? WORKER_ID_MASK : DATACENTER_ID_MASK;
        if (value < 0 || value > max) {
            throw new IllegalArgumentException(name + " 必须在 0 到 " + max + " 之间");
        }
    }
}
