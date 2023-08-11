package michelerossi.throttling;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import michelerossi.Stoppable;
import michelerossi.eventbus.EventBus;
import michelerossi.statistics.SlidingWindowStatistics;
import michelerossi.statistics.SlidingWindowStatisticsImpl;

/**
 * Time-based implementation of {@link Throttler}. <br>
 * The idea is to measure the average number of 'hits' per unit of time as measured via a sliding time window.
 */
@Slf4j
public class TimeBasedThrottler implements Throttler, Stoppable {
    private final AtomicInteger numHits = new AtomicInteger();
    private final AtomicReference<ThrottleResult> lastResult = new AtomicReference<>(ThrottleResult.DO_NOT_PROCEED);
    private final ScheduledFuture<?> samplingFuture;
    private final SlidingWindowStatistics statistics;
    private final EventBus eventBus;
    private final int maxHitsPerInterval;

    public TimeBasedThrottler(
        ScheduledExecutorService executorService,
        EventBus eventBus,
        int maxHitsPerInterval,
        int numSamples,
        int sampleInterval,
        TimeUnit sampleIntervalTimeUnit) {
        this.eventBus = eventBus;
        this.maxHitsPerInterval = maxHitsPerInterval;
        this.statistics = new SlidingWindowStatisticsImpl(eventBus, numSamples);
        this.samplingFuture = executorService.scheduleAtFixedRate(
            getAddSampleRunnable(),
            sampleInterval,
            sampleInterval,
            sampleIntervalTimeUnit);
        this.statistics.subscribeForStatistics(stats -> publishStatusUpdate(stats.getMean()));
        this.statistics.fillBuffer(maxHitsPerInterval);
    }

    private void publishStatusUpdate(double numHits) {
        var newResult = numHits < maxHitsPerInterval ? ThrottleResult.PROCEED : ThrottleResult.DO_NOT_PROCEED;
        log.info("Stats received, num hits {}, throttle {}", numHits, newResult);
        var oldResult = lastResult.getAndSet(newResult);
        if (oldResult != newResult) {
            eventBus.publishEvent(newResult);
        }
    }

    public void hit() {
        var currentNumHits = this.numHits.incrementAndGet();
        publishStatusUpdate(currentNumHits);
    }

    @Override
    public ThrottleResult shouldProceed() {
        return lastResult.get();
    }

    @Override
    public void notifyWhenCanProceed(Consumer<ThrottleResult> throttleResultConsumer) {
        eventBus.addSubscriber(ThrottleResult.class, throttleResultConsumer);
    }

    @Override
    public void stop() {
        if (samplingFuture != null) {
            samplingFuture.cancel(true);
        }
        log.info("{} stopped", this);
    }

    private Runnable getAddSampleRunnable() {
        return () -> {
            int numHitsPeriod = this.numHits.getAndSet(0);
            statistics.add(numHitsPeriod);
            log.info("Added numHits sample {}", numHitsPeriod);
        };
    }
}
