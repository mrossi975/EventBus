package michelerossi.statistics;

import java.util.function.Consumer;

/**
 * Concrete implementations produce descriptive statistics on a 'sliding window' array of samples. <br>
 * The current version only supports <code>int</code> samples. <br>
 */
public interface SlidingWindowStatistics {

    /**
     * Adds the specified measurement to the sliding window samples set.
     * @param measurement the measurement to add to the samples set
     */
    void add(int measurement);

    /**
     * Fills the internal sliding buffer with the current value.
     * Useful to kick-start the buffer in certain usage scenarios when you can not wait to
     * receive N samples to produce a valid statistics in output.
     * @param measurement
     */
    void fillBuffer(int measurement);

    /**
     * Registers the specified consumer to receive statistics when they are available.
     * @param statisticsConsumer the consumer receiving statistics
     */
    void subscribeForStatistics(Consumer<Statistics> statisticsConsumer);

    /**
     * the latest available statistics or null if no statistics are available
     * @return the latest available statistics or null if no statistics are available
     */
    Statistics getLatestStatistics();

    /**
     * Holds a number of descriptive statistics values.
     */
    interface Statistics {
        double getMean();

        int getMode();

        int getPctile(int pctile);

        int getMin();

        int getMax();
    }
}