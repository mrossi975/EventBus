package michelerossi.eventbus;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Single-threaded implementation of {@link EventBus} which notifies events on the thread calling the {@link #publishEvent(Object)} method. <br>
 * This implementation is thread-safe and allows to have the subscriptions done concurrently and on threads different from the one publishing events. <br>
 * The publishing and filtering based on the subscriber predicate are performed on the publisher thread.
 */
@Slf4j
public class SimpleEventBus implements EventBus {
    final Collection<SubscriberWithPredicate<?>> subscribers = new HashSet<>();
    private final Map<Class<?>, Collection<SubscriberWithPredicate<?>>> resolvedSubscribers = new HashMap<>();

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void dispatchEventToSub(Object event, SubscriberWithPredicate subscriber) {
        var debugEnabled = log.isDebugEnabled();
        try {
            var ts0 = debugEnabled ? System.currentTimeMillis() : 0;
            subscriber.consumer().accept(event);
            if (debugEnabled) {
                log.debug("Event {} dispatched to {} in {} ms", event, subscriber, System.currentTimeMillis() - ts0);
            }
        } catch (Exception ex) {
            log.error("Exception while attempting to dispatch event {} to subscriber {}", event, subscriber);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate<SubscriberWithPredicate> passesSubscriberFilter(Object event) {
        return subscriber -> subscriber.eventFilter().test(event);
    }

    /** @inheritDoc */
    @Override
    public void publishEvent(@NonNull Object event) {
        publishEventImpl(event, false);
    }

    /** @inheritDoc */
    @Override
    public <T> void addSubscriber(@NonNull Class<T> clazz, @NonNull Consumer<T> subscriber) {
        addSubscriberForFilteredEvents(clazz, subscriber, t -> true);
    }

    /** @inheritDoc */
    @Override
    public <T> void addSubscriberForFilteredEvents(
        @NonNull Class<T> clazz,
        @NonNull Consumer<T> subscriber,
        @NonNull Predicate<T> eventFilter) {
        synchronized (this) {
            boolean subscriberAdded = subscribers.add(new SubscriberWithPredicate<>(subscriber, clazz, eventFilter));
            if (!subscriberAdded) {
                throw new IllegalStateException("Subscriber " + subscriber + " already registered to receive " + clazz + " events");
            }
            resolvedSubscribers.clear();
        }

        log.info("{} subscribed to {} events with filter {}", subscriber, clazz, eventFilter);
    }

    void publishEventImpl(Object event, boolean coalesce) {
        var clazz = event.getClass();
        Collection<SubscriberWithPredicate<?>> subscribersForClass;
        synchronized (this) {
            subscribersForClass = resolvedSubscribers.computeIfAbsent(clazz, clz -> subscribers.stream().filter(sub -> sub.clazz().isAssignableFrom(clazz)).toList());
        }

        subscribersForClass
            .stream()
            .filter(passesSubscriberFilter(event))
            .forEach(subscriber -> {
                log.trace("Dispatching event {} to {} coalescing {}", event, subscriber, coalesce);
                dispatch(event, subscriber, coalesce);
            });
    }

    @SuppressWarnings({"rawtypes"})
    void dispatch(Object event, SubscriberWithPredicate subscriber, boolean coalesce) {
        dispatchEventToSub(event, subscriber);
    }
}
