package utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Bounds how many Argon2 operations may run at the same time.
 *
 * A single hash holds roughly 78 MB of heap for its whole duration, so a
 * handful of parallel authentication requests is enough to exhaust a small
 * heap. Limiting the concurrency keeps that cost predictable regardless of how
 * many worker threads the server happens to have, and regardless of whether a
 * reverse proxy in front of the application limits concurrency correctly.
 *
 * The budget is derived from the heap that is actually available, so raising
 * -Xmx raises the limit without a configuration change.
 *
 * Calls must not be nested: a thread that holds a permit and waits for a second
 * one can deadlock the pool.
 */
public final class PasswordHashing {
    private static final Logger LOG = LogManager.getLogger(PasswordHashing.class);

    /** 78 MB per hash plus headroom for the surrounding request. */
    private static final long BYTES_PER_HASH = 96L * 1024 * 1024;

    /** Half of the heap is the budget; the rest belongs to the application. */
    private static final int HEAP_SHARE = 2;
    private static final int MIN_PERMITS = 2;
    private static final int MAX_PERMITS = 8;
    private static final long WAIT_MILLIS = 5000;

    private static final Semaphore PERMITS = new Semaphore(permits(), true);

    private PasswordHashing() {
    }

    static int permits() {
        long budget = Runtime.getRuntime().maxMemory() / HEAP_SHARE;
        long derived = budget / BYTES_PER_HASH;

        return (int) Math.max(MIN_PERMITS, Math.min(MAX_PERMITS, derived));
    }

    /**
     * Runs an operation that performs Argon2 hashing, waiting for a free slot.
     *
     * @param operation the hashing operation
     * @param rejected the value to return when no slot became available in time
     * @return the result of the operation, or the given fallback value
     */
    public static <T> T gated(Supplier<T> operation, T rejected) {
        var acquired = false;
        try {
            acquired = PERMITS.tryAcquire(WAIT_MILLIS, TimeUnit.MILLISECONDS);
            if (!acquired) {
                LOG.warn("Rejected a password hashing operation, all {} slots were busy", PERMITS.availablePermits());
                return rejected;
            }

            return operation.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return rejected;
        } finally {
            if (acquired) {
                PERMITS.release();
            }
        }
    }
}
