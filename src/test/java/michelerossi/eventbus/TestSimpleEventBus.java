package michelerossi.eventbus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** JUnit tests for {@link SimpleEventBus} */
@Slf4j
class TestSimpleEventBus {

    @Test
    void testSuperClassSubscription() {
        var eventBus = new SimpleEventBus();
        var ref = new AtomicInteger();
        Consumer<Number> consumer = i -> {
            log.info("{} is an {} number", i, i.intValue() % 2 == 0 ? "Even" : "Odd");
            ref.set(i.intValue());
        };
        eventBus.addSubscriber(Number.class, consumer);
        eventBus.publishEvent(36);
        assertEquals(36, ref.get());
    }

    @Test
    void testSuperInterfaceSubscription() {
        var eventBus = new SimpleEventBus();
        var ref = new AtomicInteger();
        Consumer<TestMessage> consumer = i -> {
            log.info("{} is an {} number", i, i.value() % 2 == 0 ? "Even" : "Odd");
            ref.set(i.value());
        };
        eventBus.addSubscriber(TestMessage.class, consumer);
        eventBus.publishEvent((TestMessage) () -> 36);
        assertEquals(36, ref.get());
    }


    @Test
    void testDoubleSubscriptionFails() {
        var eventBus = new SimpleEventBus();
        Consumer<Integer> consumer = i -> {
            log.info("{} is an {} number", i, i % 2 == 0 ? "Even" : "Odd");
        };
        eventBus.addSubscriber(Integer.class, consumer);
        assertThrows(IllegalStateException.class, () -> eventBus.addSubscriber(Integer.class, consumer));
    }

    @Test
    void testEventDispatchedAfterException1() {
        var eventBus = new SimpleEventBus();
        var valueRef = new AtomicInteger(0);
        Consumer<Integer> consumer = valueRef::set;
        Consumer<Integer> exceptionConsumer = i -> {
            throw new NullPointerException("Some bug in this consumer");
        };
        eventBus.addSubscriber(Integer.class, exceptionConsumer);
        eventBus.addSubscriber(Integer.class, consumer);
        eventBus.publishEvent(3);
        assertEquals(3, valueRef.get());
    }

    @Test
    void testEventDispatchedAfterException2() {
        var eventBus = new SimpleEventBus();
        var valueRef = new AtomicInteger(0);
        Consumer<Integer> consumer = valueRef::set;
        Consumer<Integer> exceptionConsumer = i -> {
            throw new NullPointerException("Some bug in this consumer");
        };
        eventBus.addSubscriber(Integer.class, consumer);
        eventBus.addSubscriber(Integer.class, exceptionConsumer);
        eventBus.publishEvent(3);
        assertEquals(3, valueRef.get());
    }

    @Test
    void testDefaultCoalescingImplementation() {
        var eventBus = new SimpleEventBus();
        var values = new ArrayList<>();
        Consumer<Integer> consumer = values::add;

        eventBus.addSubscriber(Integer.class, consumer);
        eventBus.publishEventCoalesce(3);
        eventBus.publishEventCoalesce(4);
        eventBus.publishEventCoalesce(5);
        eventBus.publishEventCoalesce(6);
        assertEquals(List.of(3, 4, 5, 6), values);
    }

    @Test
    void testSubscriptionFilter() {
        var eventBus = new SimpleEventBus();
        var valueRef = new AtomicInteger(0);
        Consumer<Integer> consumer = valueRef::set;
        eventBus.addSubscriberForFilteredEvents(Integer.class, consumer, num -> num % 2 == 0);
        eventBus.publishEvent(3);

        // the event didn't go through
        assertEquals(0, valueRef.get());

        eventBus.publishEvent(4);
        assertEquals(4, valueRef.get());
    }

    private interface TestMessage {
        int value();
    }
}
