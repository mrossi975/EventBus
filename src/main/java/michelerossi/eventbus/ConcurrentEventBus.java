package michelerossi.eventbus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import michelerossi.Stoppable;

/**
 * Concurrent {@link EventBus} implementation capable of storing events and publishing them asynchronously. <br>
 * A dispatch thread is allocated for each event type. <br>
 * This does not pose a risk under normal usage circumstances as the number of event types is unlikely to keep increasing
 * once the application has reached a steady state. <br>
 * Ideally this should be refactored to avoid using inheritance from SimpleEventBus.
 * Other dispatch strategies might be more appropriate for different usage scenarios.
 */
@Slf4j
public class ConcurrentEventBus extends SimpleEventBus implements EventBus, Stoppable {
    private final Map<Class<?>, DispatcherForType> dispatchersForType = new HashMap<>();

    private static Function<? super Class<?>, DispatcherForType> createDispatcherForType(SubscriberWithPredicate<?> subscriber) {
        return clz -> {
            var eventsList = new ArrayList<>();
            var dispatchRunnable = getDispatchRunnable(subscriber, eventsList);
            var dispatchThread = new Thread(dispatchRunnable, "DispatchThread-" + clz.getSimpleName());
            dispatchThread.start();
            return new DispatcherForType(clz, eventsList, dispatchThread);
        };
    }

    @SuppressWarnings({"java:S2445", "rawtypes", "InfiniteLoopStatement"})
    private static Runnable getDispatchRunnable(SubscriberWithPredicate subscriber, Collection<Object> eventsList) {
        return () -> {
            var threadName = Thread.currentThread().getName();
            log.info("{} dispatching {} events started", threadName, subscriber.clazz().getSimpleName());

            try {
                while (true) {
                    var eventsListCopy = copyAndClearQueuedEvents(subscriber, eventsList, threadName);
                    dispatchEvents(subscriber, eventsListCopy);
                }
            } catch (InterruptedException ie) {
                var msg = String.format("%s interrupted, thread terminating", threadName);
                log.debug(msg);
                Thread.currentThread().interrupt();
            }
        };
    }

    @SuppressWarnings({"rawtypes"})
    private static void dispatchEvents(SubscriberWithPredicate subscriber, List<Object> eventsListCopy) {
        var eventsIterator = eventsListCopy.iterator();
        while (eventsIterator.hasNext()) {
            var eventToDispatch = eventsIterator.next();
            eventsIterator.remove();
            dispatchEventToSub(eventToDispatch, subscriber);
        }
    }

    @SuppressWarnings({"java:S2445", "java:S2274", "rawtypes", "SynchronizationOnLocalVariableOrMethodParameter"})
    private static List<Object> copyAndClearQueuedEvents(SubscriberWithPredicate subscriber, Collection<Object> eventsList, String threadName) throws InterruptedException {
        var eventsListCopy = new ArrayList<>();

        synchronized (eventsList) {
            eventsList.wait(1000);
            if (eventsList.isEmpty()) {
                log.debug("{} no {} events to dispatch to {}", threadName, subscriber.clazz().getSimpleName(), subscriber);
            } else {
                log.debug("{} dispatching {} events of type {} to {}", threadName, eventsList.size(), subscriber.clazz().getSimpleName(), subscriber);
                eventsListCopy.addAll(eventsList);
                eventsList.clear();
            }
        }
        return eventsListCopy;
    }

    /** {@inheritDoc} */
    @Override
    public void publishEventCoalesce(@NonNull Object event) {
        publishEventImpl(event, true);
    }

    /**
     * Stops and destroys all dispatcher threads.
     * Subsequent attempt to publish events will simply lead to the creation of new dispatcher threads.
     * Events might be lost when using this operation which is normally to be used only for cleanup purposes
     */
    @Override
    public void stop() {
        synchronized (dispatchersForType) {
            dispatchersForType.values().forEach(dispatcher -> {
                dispatcher.dispatchThread().interrupt();
            });
            dispatchersForType.clear();
        }
    }

    @SuppressWarnings({"rawtypes"})
    @Override
    void dispatch(Object event, SubscriberWithPredicate subscriber, boolean coalesce) {
        var clazz = event.getClass();
        DispatcherForType dispatcherForType;
        synchronized (dispatchersForType) {
            dispatcherForType = dispatchersForType.computeIfAbsent(clazz, createDispatcherForType(subscriber));
        }
        var events = dispatcherForType.events();
        synchronized (events) {
            if (coalesce) {
                log.debug("Clearing existing {} queued events for type {} as coalescing is enabled", events.size(), clazz);
                events.clear(); // this could be done much more efficiently by creating a one position implementation of Collection
            }
            events.add(event);
            log.debug("Event {} of type {} queued for dispatch ({} items queued)", event, clazz, events.size());
            events.notifyAll();
        }
    }

    private record DispatcherForType(@NonNull Class<?> eventType,
                                     @NonNull Collection<Object> events,
                                     @NonNull Thread dispatchThread) {
    }
}
