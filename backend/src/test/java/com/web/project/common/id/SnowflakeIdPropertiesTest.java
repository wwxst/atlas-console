package com.web.project.common.id;

import com.web.project.config.properties.SnowflakeIdProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnowflakeIdPropertiesTest {

    @Test
    void acceptsFiveBitWorkerAndDatacenterIds() {
        SnowflakeIdProperties properties = new SnowflakeIdProperties(31, 0);

        assertEquals(31, properties.datacenterId());
        assertEquals(0, properties.workerId());
    }

    @Test
    void rejectsWorkerOrDatacenterIdsOutsideFiveBits() {
        assertDoesNotThrow(() -> new SnowflakeIdProperties(0, 31));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdProperties(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdProperties(0, 32));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdProperties(32, 0));
    }
}
