package michelerossi.statistics;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import michelerossi.eventbus.SimpleEventBus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Tests for {@link SlidingWindowStatisticsImpl} */
class TestSlidingWindowStatistics {

    @Test
    void testStats1() {
        var eventBus = new SimpleEventBus();
        var statsRef = new AtomicReference<SlidingWindowStatistics.Statistics>();
        eventBus.addSubscriber(SlidingWindowStatistics.Statistics.class, statsRef::set);
        var stats = new SlidingWindowStatisticsImpl(eventBus, 5);
        stats.add(1);
        stats.add(1);
        assertNull(stats.getLatestStatistics());
        assertNull(statsRef.get());

        stats.add(1);
        stats.add(1);
        stats.add(1);

        assertNotNull(stats.getLatestStatistics());
        assertNotNull(statsRef.get());
        assertEquals(stats.getLatestStatistics(), statsRef.get());
        assertEquals(1.0, stats.getLatestStatistics().getMean(), 0.00001);
        assertEquals(1, stats.getLatestStatistics().getMode());
        assertEquals(1, stats.getLatestStatistics().getPctile(95));
    }

    @Test
    void testStats2() {
        var eventBus = new SimpleEventBus();
        var statsRef = new AtomicReference<SlidingWindowStatistics.Statistics>();
        var numSamples = 1000;
        var stats = new SlidingWindowStatisticsImpl(eventBus, numSamples);
        stats.subscribeForStatistics(statsRef::set);
        var random = new Random(System.nanoTime());
        IntStream.range(0, numSamples).forEach(ix -> stats.add((int) random.nextGaussian(1000, 100)));
        var latestStats = stats.getLatestStatistics();
        assertEquals(1000, latestStats.getMean(), 15);
        assertEquals(1000, latestStats.getMode(), 100);
        assertEquals(1000, latestStats.getPctile(50), 15);
        assertEquals(1100, latestStats.getPctile(87), 25);
        assertEquals(latestStats, statsRef.get());
    }

    @Test
    void testStats3() {
        var eventBus = new SimpleEventBus();
        var statsRef = new AtomicReference<SlidingWindowStatistics.Statistics>();
        var stats = new SlidingWindowStatisticsImpl(eventBus, 3);
        stats.subscribeForStatistics(statsRef::set);

        stats.add(1);
        stats.add(2);
        stats.add(3);
        stats.add(2);
        stats.add(1);

        assertEquals(1, statsRef.get().getMin());
        assertEquals(3, statsRef.get().getMax());
    }
}
