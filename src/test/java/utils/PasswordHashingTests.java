package utils;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

public class PasswordHashingTests {

    @Test
    public void testPermitsStayWithinBounds() {
        int permits = PasswordHashing.permits();

        assertThat(permits, greaterThanOrEqualTo(2));
        assertThat(permits, lessThanOrEqualTo(8));
    }

    @Test
    public void testOperationRunsAndReturnsItsResult() {
        assertThat(PasswordHashing.gated(() -> "done", "rejected"), equalTo("done"));
    }

    @Test
    public void testConcurrencyIsCappedAtThePermitCount() throws Exception {
        int permits = PasswordHashing.permits();
        int threads = permits * 4;

        var inFlight = new AtomicInteger();
        var peak = new AtomicInteger();
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);

        for (var i = 0; i < threads; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    start.await();
                    PasswordHashing.gated(() -> {
                        peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                        try {
                            // stand in for the ~250 ms an argon2 hash takes
                            Thread.sleep(Duration.ofMillis(50));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        inFlight.decrementAndGet();
                        return Boolean.TRUE;
                    }, Boolean.FALSE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS), equalTo(true));

        // this is the property the finding is about: the number of simultaneous
        // 78 MB allocations never exceeds the budget, no matter how many
        // requests arrive at once
        assertThat(peak.get(), lessThanOrEqualTo(permits));
    }

    @Test
    public void testPermitIsReleasedWhenTheOperationThrows() {
        for (var i = 0; i < 20; i++) {
            try {
                PasswordHashing.gated(() -> {
                    throw new IllegalStateException("boom");
                }, Boolean.FALSE);
            } catch (IllegalStateException e) {
                // expected
            }
        }

        // a leaked permit would make this call time out and return the fallback
        assertThat(PasswordHashing.gated(() -> Boolean.TRUE, Boolean.FALSE), equalTo(true));
    }
}
