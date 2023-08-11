package michelerossi.statistics;

import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import michelerossi.eventbus.EventBus;

/**
 * An implementation of {@link SlidingWindowStatistics} which based on a {@link IntShiftBuffer}
 * which uses an {@link EventBus} to deliver statistics asynchronously.
 */
public class SlidingWindowStatisticsImpl implements SlidingWindowStatistics {
    private final IntShiftBuffer shiftBuffer;
    private final AtomicReference<Statistics> latestStats = new AtomicReference<>();
    private final EventBus eventBus;

    /**
     * Constructor
     * @param eventBus   the eventBus to use to dispatch statistics objects asynchronously
     * @param numSamples the number of samples to use to calculate descriptive statitics
     */
    public SlidingWindowStatisticsImpl(EventBus eventBus, int numSamples) {
        this.shiftBuffer = new IntShiftBuffer(numSamples);
        this.eventBus = eventBus;
    }

    private static Statistics calculateStats(int[] samples) {
        Arrays.sort(samples);
        var modeAndMean = findMinMaxModeMean(samples);
        return new Statistics() {
            @Override
            public double getMean() {
                return modeAndMean.mean();
            }

            @Override
            public int getMode() {
                return modeAndMean.mode();
            }

            @Override
            public int getPctile(int pctile) {
                var rank = (int) Math.floor((pctile / 100.0) * (samples.length + 1));
                return samples[rank - 1];
            }

            @Override
            public int getMin() {
                return modeAndMean.min();
            }

            @Override
            public int getMax() {
                return modeAndMean.max();
            }
        };
    }

    private static ModeMeanMinMax findMinMaxModeMean(int[] array) {
        var frequencyMap = new HashMap<Integer, Integer>();
        var total = 0.0;
        var min = Integer.MAX_VALUE;
        var max = Integer.MIN_VALUE;
        for (int num : array) {
            total += num;
            if (num <= min) {
                min = num;
            }
            if (num >= max) {
                max = num;
            }
            frequencyMap.put(num, frequencyMap.getOrDefault(num, 0) + 1);
        }

        int mode = 0;
        int maxFrequency = 0;

        for (var entry : frequencyMap.entrySet()) {
            int frequency = frequencyMap.get(entry.getKey());
            if (frequency > maxFrequency) {
                maxFrequency = frequency;
                mode = entry.getKey();
            }
        }

        return new ModeMeanMinMax(mode, total / array.length, min, max);
    }

    /** {@inheritDoc} */
    @Override
    public void add(int measurement) {
        this.shiftBuffer.add(measurement);
        if (this.shiftBuffer.getBufferSize() == this.shiftBuffer.getCurrentSize()) {
            var stats = calculateStats(this.shiftBuffer.getSamples());
            latestStats.set(stats);
            eventBus.publishEvent(stats);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void fillBuffer(int measurement) {
        for (int i = 0; i < this.shiftBuffer.getBufferSize(); i++) {
            add(measurement);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void subscribeForStatistics(Consumer<Statistics> statisticsConsumer) {
        eventBus.addSubscriber(Statistics.class, statisticsConsumer);
    }

    /** {@inheritDoc} */
    @Override
    public Statistics getLatestStatistics() {
        return latestStats.get();
    }

    private record ModeMeanMinMax(int mode, double mean, int min, int max) {
    }
}
