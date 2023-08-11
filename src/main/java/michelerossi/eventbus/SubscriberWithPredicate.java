package michelerossi.eventbus;

import java.util.function.Consumer;
import java.util.function.Predicate;

import lombok.NonNull;

/**
 * Used by {@link EventBus} implementations to hold a subscriber with its filter and event class.
 * Not part of the public EventBus API.
 * @param consumer
 * @param eventFilter
 * @param <T>
 */
record SubscriberWithPredicate<T>(
    @NonNull Consumer<T> consumer,
    @NonNull Class<T> clazz,
    @NonNull Predicate<T> eventFilter) {
}
