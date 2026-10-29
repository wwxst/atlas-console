package com.web.project.common.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowflakeIdGeneratorTest {

    @Test
    void generatedIdsAreMonotonicWithinOneMillisecond() {
        long timestamp = System.currentTimeMillis();
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0, 0, () -> timestamp);

        long previous = generator.nextId();
        for (int index = 0; index < 100; index++) {
            long current = generator.nextId();
            assertTrue(current > previous);
            previous = current;
        }
    }

    @Test
    void generatedIdsAreUniqueWhenCalledConcurrently() throws Exception {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(3, 7);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        try {
            Set<Future<?>> tasks = new HashSet<>();
            for (int task = 0; task < 8; task++) {
                tasks.add(executor.submit(() -> {
                    for (int index = 0; index < 500; index++) {
                        ids.add(generator.nextId());
                    }
                }));
            }
            for (Future<?> task : tasks) {
                task.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(4000, ids.size());
    }

    @Test
    void generatedIdContainsConfiguredWorkerAndDatacenterBits() {
        long timestamp = System.currentTimeMillis();
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(23, 17, () -> timestamp);

        long id = generator.nextId();

        assertEquals(17, (id >>> 12) & 0x1F);
        assertEquals(23, (id >>> 17) & 0x1F);

        SnowflakeIdGenerator differentWorker = new SnowflakeIdGenerator(23, 18, () -> timestamp);
        assertNotEquals(id, differentWorker.nextId());
    }

    @Test
    void sequenceExhaustionMovesToTheNextMillisecond() {
        long timestamp = System.currentTimeMillis();
        AtomicInteger clockCalls = new AtomicInteger();
        LongSupplier clock = () -> clockCalls.getAndIncrement() < 4097
                ? timestamp
                : timestamp + 1;
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0, 0, clock);

        long lastId = 0;
        for (int index = 0; index < 4096; index++) {
            lastId = generator.nextId();
        }
        long rolloverId = generator.nextId();

        assertTrue(rolloverId > lastId);
        assertEquals(0, rolloverId & 0xFFF);
    }

    @Test
    void clockRollbackDuringSequenceWaitIsRejected() {
        long timestamp = System.currentTimeMillis();
        AtomicInteger clockCalls = new AtomicInteger();
        LongSupplier clock = () -> clockCalls.getAndIncrement() < 4097
                ? timestamp
                : timestamp - 1;
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0, 0, clock);

        for (int index = 0; index < 4096; index++) {
            generator.nextId();
        }

        assertThrows(IllegalStateException.class, generator::nextId);
    }

    @Test
    void clockRollbackDuringSequenceWaitPreservesSequenceForRecovery() {
        long timestamp = System.currentTimeMillis();
        AtomicInteger clockCalls = new AtomicInteger();
        LongSupplier clock = () -> {
            int call = clockCalls.getAndIncrement();
            if (call < 4097 || call == 4098) {
                return timestamp;
            }
            return call == 4097 ? timestamp - 1 : timestamp + 1;
        };
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0, 0, clock);
        long lastId = 0;
        for (int index = 0; index < 4096; index++) {
            lastId = generator.nextId();
        }

        assertThrows(IllegalStateException.class, generator::nextId);

        assertTrue(generator.nextId() > lastId, "Recovery must not reissue an ID from the exhausted millisecond");
    }

    @Test
    void clockRollbackIsRejected() {
        AtomicLong clock = new AtomicLong(System.currentTimeMillis());
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0, 0, clock::get);
        generator.nextId();

        clock.decrementAndGet();

        assertThrows(IllegalStateException.class, generator::nextId);
    }
}
