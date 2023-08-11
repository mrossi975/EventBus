package michelerossi.eventbus;

import java.util.function.Consumer;
import java.util.function.Predicate;

import lombok.NonNull;

/**
 * A generic event bus - see concrete implementations for more details on performances and limitations.
 */
public interface EventBus {

    /**
     * Publishes the specified event to the subscribers registered to consume events of the class of this object.
     * @param event the event to publish
     * @see #addSubscriber(Class, Consumer)
     * @see #addSubscriberForFilteredEvents(Class, Consumer, Predicate)
     */
    void publishEvent(@NonNull Object event);

    /**
     * Publishes the specified event indicating to the underlying implementation to send only the latest value
     * if previous values of the same type still haven't been sent when this method is called
     * @param event the event to publish
     */
    default void publishEventCoalesce(@NonNull Object event) {
        publishEvent(event);
    }

    /**
     * Registers the specified consumer to receive events of the specified class.
     * Note that the subscriber will receive events matching any subclass of the specified class.
     * Additionally, the subscriber will receive events of all types that implement the specified interface.
     * @param clazz      the class of the events to send to the consumer
     * @param subscriber the consumer of the events
     * @param <T>        the type of events
     */
    <T> void addSubscriber(@NonNull Class<T> clazz, @NonNull Consumer<T> subscriber);

    /**
     * Registers the specified consumer to receive events of the specified class which pass the specified predicate.
     * @param clazz       the class of the events to send to the consumer
     * @param subscriber  the consumer of the events
     * @param eventFilter a predicate used to filter events to send to this subscriber
     * @param <T>         the type of events
     */
    <T> void addSubscriberForFilteredEvents(@NonNull Class<T> clazz, @NonNull Consumer<T> subscriber, @NonNull Predicate<T> eventFilter);
}