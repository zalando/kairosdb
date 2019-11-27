package org.kairosdb.datastore.cassandra.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;


public class CacheWarmingUpLogic {
    private static final Logger logger = LoggerFactory.getLogger(CacheWarmingUpLogic.class);
    private final Queue<BooleanSupplier> queue = new ConcurrentLinkedQueue<>();

    public boolean isWarmingUpNeeded(final int hashCode, final long currentTime, final long nextBucketStartsAt,
                                     final int minutesBeforeNextBucket, int rowSize) {
        final long warmingUpPeriodStartsAt = nextBucketStartsAt - minutesBeforeNextBucket * 1000 * 60;
        if (currentTime < warmingUpPeriodStartsAt) {
            return false;
        }
        final int numberOfRows = minutesBeforeNextBucket / rowSize;
        final long currentRowOfGracePeriod = (currentTime - warmingUpPeriodStartsAt) / 1000 / 60 / rowSize;
        final int modulo = Math.abs(hashCode % numberOfRows);
        boolean result = modulo == currentRowOfGracePeriod;
        logger.debug(String.format("Result '%b' is calculated based on following: " +
                        "hash code of byte buffer = '%d', " +
                        "number of rows = '%d', current row of grace period = '%d', " +
                        "hashCode %% numberOfRows is '%d'",
                result, hashCode, numberOfRows, currentRowOfGracePeriod, modulo));
        return result;
    }

    public boolean isWarmingUpNeeded(final AtomicLong counter) {
        return counter.decrementAndGet() > 0;
    }

    public boolean shouldWarmingUpWork(final long currentTime, final long nextBucketStartsAt, final int minutesBeforeNextBucket) {
        final long warmingUpPeriodStartsAt = nextBucketStartsAt - minutesBeforeNextBucket * 1000 * 60;
        return currentTime > warmingUpPeriodStartsAt;
    }

    public void addToQueue(BooleanSupplier supplier) {
        queue.offer(supplier);
    }

    public void runWarmingUp(final AtomicLong counter) {
        logger.warn("queue size before run is " + queue.size());
        while (counter.decrementAndGet() > 0) {
            BooleanSupplier supplier = queue.poll();
            if (supplier == null) {
                break;
            }
            boolean isAdded = supplier.getAsBoolean();
            if (!isAdded) {
                counter.incrementAndGet();
            }
        }
        logger.warn("queue size after run is " + queue.size());
    }
}
