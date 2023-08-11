package michelerossi.throttling;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

import michelerossi.Stoppable;
import michelerossi.eventbus.ConcurrentEventBus;
import michelerossi.eventbus.SimpleEventBus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tests for {@link michelerossi.throttling.TimeBasedThrottler} */
@Slf4j
class TimeBasedThrottlerTest {
    private static ScheduledExecutorService executorService;

    @BeforeAll
    static void initExecutorService() {
        executorService = Executors.newScheduledThreadPool(5);
    }

    @AfterAll
    static void shutdownExecutorService() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    private static void sleep(long timeMs) {
        try {
            Thread.sleep(timeMs);
        } catch (InterruptedException ex) {
            // ignored
        }
    }

    @Test
    void testTimeBasedThrottler1() {
        var eventBus = new SimpleEventBus();

        int timeWindowMs = 5_000;
        int expectedNumActions = 5;
        int maxHitsPerInterval = 1;
        int numSamples = 5;
        int sampleIntervalMs = 1000;
        var throttler = new TimeBasedThrottler(
            executorService,
            eventBus,
            maxHitsPerInterval,
            numSamples,
            sampleIntervalMs,
            TimeUnit.MILLISECONDS);

        assertEquals(Throttler.ThrottleResult.DO_NOT_PROCEED, throttler.shouldProceed());

        long start = System.currentTimeMillis();
        long end = start + timeWindowMs;
        boolean finished;

        int numActionsPerformed = 0;

        do {
            if (throttler.shouldProceed() == Throttler.ThrottleResult.PROCEED) {
                numActionsPerformed++;
                throttler.hit();
                log.info(">>>>>>> Action performed");
            } else {
                log.info("******* Unable to perform action, throttling in progress");
            }
            finished = System.currentTimeMillis() > end;
            sleep(5);
        } while (!finished);

        assertEquals(expectedNumActions, numActionsPerformed, 5);
        throttler.stop();
    }

    @Test
    void testTimeBasedThrottler2() {
        var eventBus = new ConcurrentEventBus();
        int expectedNumActions = 5;
        int maxHitsPerInterval = 10;
        int numSamples = 5;
        int sampleIntervalMs = 1000;
        var throttler = new TimeBasedThrottler(
            executorService,
            eventBus,
            maxHitsPerInterval,
            numSamples,
            sampleIntervalMs,
            TimeUnit.MILLISECONDS);

        assertEquals(Throttler.ThrottleResult.DO_NOT_PROCEED, throttler.shouldProceed());

        var stoppableActionPerformer = new StoppableActionPerformer(throttler);

        throttler.notifyWhenCanProceed(throttleResult -> {
            if (throttleResult == Throttler.ThrottleResult.PROCEED) {
                stoppableActionPerformer.startPerformAction();
            } else {
                stoppableActionPerformer.stopPerformAction();
            }
        });

        sleep(10_000);
        assertEquals(expectedNumActions, stoppableActionPerformer.getNumActionsPerformed(), 5);
        throttler.stop();
    }

    private static class StoppableActionPerformer implements Stoppable {
        private final AtomicBoolean runStatus = new AtomicBoolean(false);
        private final Thread workerThread;

        private final AtomicInteger numActionsPerformed = new AtomicInteger(0);

        private StoppableActionPerformer(TimeBasedThrottler throttler) {
            this.workerThread = new Thread(
                () -> {
                    while (true) {
                        if (runStatus.get()) {
                            numActionsPerformed.incrementAndGet();
                            throttler.hit();
                        }
                        sleep(1);
                    }
                },
                "StoppableActionPerformerThread");
        }

        public int getNumActionsPerformed() {
            return numActionsPerformed.get();
        }

        public void startPerformAction() {
            this.runStatus.set(true);
        }

        public void stopPerformAction() {
            this.runStatus.set(false);
        }

        @Override
        public void stop() {
            if (workerThread != null) {
                workerThread.interrupt();
            }
        }
    }
}
