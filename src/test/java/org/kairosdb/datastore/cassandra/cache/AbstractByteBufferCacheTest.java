package org.kairosdb.datastore.cassandra.cache;

import org.junit.Test;
import org.kairosdb.core.admin.CacheMetricsProvider;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class AbstractByteBufferCacheTest {

    private class FakeCache extends AbstractByteBufferCache {
        FakeCache(CacheMetricsProvider cacheMetricsProvider, int maxSize, int ttlInSeconds, String cacheId) {
            super(cacheMetricsProvider, maxSize, ttlInSeconds, cacheId);
        }
    }

    @Test
    public void testDoubleHashIs128bit() {
        final AbstractByteBufferCache cache = mock(AbstractByteBufferCache.class);
        when(cache.doubleHash(any(ByteBuffer.class))).thenCallRealMethod();
        final BigInteger got = cache.doubleHash(ByteBuffer.wrap(new byte[]{0x42, 0x69}));
        assertNotNull(got);
        assertThat(got.bitLength(), is(127)); // excludes the sign bit
    }

    @Test
    public void testIsKnown() {
        CacheMetricsProvider metrics = mock(CacheMetricsProvider.class);
        final AbstractByteBufferCache cache = new FakeCache(metrics, 10, 60, "fake");

        cache.put(ByteBuffer.wrap(new byte[]{0x11, 0x11}));
        assertTrue(cache.isKnown(ByteBuffer.wrap(new byte[]{0x11, 0x11})));

        assertFalse(cache.isKnown(ByteBuffer.wrap(new byte[]{0x22, 0x22})));
    }
}