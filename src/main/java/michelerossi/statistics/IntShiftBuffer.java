package michelerossi.statistics;

import lombok.Getter;

/**
 * An array based implementation of a shift-buffer that supports only primitive int values. <br>
 * The advantage of this implementation compared to a LinkedList or another queue is the reduced
 * garbage collection impact since the array is only allocated once.
 * This implementation is thread-safe and can be shared among different threads without requiring any additional synchronization.
 */
public class IntShiftBuffer {
    private final int[] values;

    @Getter
    private final int bufferSize;
    private int currentIndex;
    private int totalNumItems;

    /**
     * Constructor
     * @param bufferSize the number of items to keep in the shift buffer (or sliding window)
     */
    public IntShiftBuffer(int bufferSize) {
        this.values = new int[bufferSize];
        this.bufferSize = bufferSize;
        this.currentIndex = 0;
    }

    /**
     * Adds a new sample to the shift buffer
     * @param value the int value to add to the shift buffer
     */
    public void add(int value) {
        synchronized (this) {
            values[currentIndex] = value;
            currentIndex = (currentIndex + 1) % bufferSize;
            if (totalNumItems < this.bufferSize) {
                totalNumItems++;
            }
        }
    }

    /**
     * Returns a copy of the current set of samples
     * @return a copy of the current set of samples
     */
    public int[] getSamples() {
        var windowSamples = new int[bufferSize];
        synchronized (this) {
            for (int i = 0; i < bufferSize; i++) {
                windowSamples[i] = values[(currentIndex + i) % bufferSize];
            }
        }
        return windowSamples;
    }

    /**
     * Returns the current number of samples present in the buffer.
     * @return the current number of samples present in the buffer
     */
    public int getCurrentSize() {
        synchronized (this) {
            return totalNumItems;
        }
    }
}
