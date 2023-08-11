package michelerossi.throttling;

import java.util.function.Consumer;

/**
 * Used to determine whether a certain action should go ahead or not depending on the throttling status.
 */
public interface Throttler {
    /** Returns the current throttle status */
    ThrottleResult shouldProceed();

    /** Consumers are notified when they should proceed or stop */
    void notifyWhenCanProceed(Consumer<ThrottleResult> throttleResultConsumer);

    enum ThrottleResult {
        PROCEED,
        DO_NOT_PROCEED
    }
}