package michelerossi.eventbus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for {@link ConcurrentEventBus} */
class TestConcurrentEventBus {


    private static void sleep(long sleepTimeMs) {
        try {
            Thread.sleep(sleepTimeMs);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testAsynchDispatchSlowConsumer() throws InterruptedException {
        var concurrentBus = new ConcurrentEventBus();
        concurrentBus.addSubscriber(Integer.class, itg -> {
            sleep(itg);
        });
        var strRef = new AtomicReference<String>();
        var cdLatch = new CountDownLatch(1);
        concurrentBus.addSubscriber(String.class, str -> {
            strRef.set(str);
            cdLatch.countDown();
        });

        concurrentBus.publishEvent(60_000);
        concurrentBus.publishEvent("Michele");
        assertTrue(cdLatch.await(5, TimeUnit.SECONDS));
        assertEquals("Michele", strRef.get());
        concurrentBus.stop();
    }

    @Test
    void testCoalescing() throws InterruptedException {
        var concurrentBus = new ConcurrentEventBus();
        var values = new ArrayList<Integer>();
        var cdLatch = new CountDownLatch(3);
        concurrentBus.addSubscriber(Integer.class, itg -> {
            sleep(itg);
            values.add(itg);
            cdLatch.countDown();
        });

        concurrentBus.publishEventCoalesce(1000);
        sleep(2500); // necessary to let the dispatch thread start up
        concurrentBus.publishEventCoalesce(1100);
        concurrentBus.publishEventCoalesce(1200);
        concurrentBus.publishEventCoalesce(1250);
        concurrentBus.publishEventCoalesce(1280);
        concurrentBus.publishEventCoalesce(1300);

        assertTrue(cdLatch.await(10, TimeUnit.SECONDS));
        assertEquals(List.of(1000, 1100, 1300), values);
        concurrentBus.stop();
    }

    @Test
    void testCoalescing2() throws InterruptedException {
        var concurrentBus = new ConcurrentEventBus();
        var values = new ArrayList<Integer>();
        var cdLatch = new CountDownLatch(2);
        concurrentBus.addSubscriber(Integer.class, itg -> {
            sleep(itg);
            values.add(itg);
            cdLatch.countDown();
        });

        concurrentBus.publishEventCoalesce(4000);
        sleep(2500);
        concurrentBus.publishEventCoalesce(1100);
        concurrentBus.publishEventCoalesce(1200);
        concurrentBus.publishEventCoalesce(1300);
        concurrentBus.publishEventCoalesce(1400);
        concurrentBus.publishEventCoalesce(1500);

        assertTrue(cdLatch.await(10, TimeUnit.SECONDS));
        assertEquals(List.of(4000, 1500), values);
        concurrentBus.stop();

    }

    @Test
    void testExceptionHandling() throws InterruptedException {
        var concurrentBus = new ConcurrentEventBus();
        var values = new ArrayList<Integer>();
        var cdLatch = new CountDownLatch(3);
        concurrentBus.addSubscriber(Integer.class, itg -> {
            if (itg % 2 == 0) {
                throw new NullPointerException("Test bug in the consumer");
            }
            sleep(itg);
            values.add(itg);
            cdLatch.countDown();
        });

        concurrentBus.publishEvent(4001);
        sleep(2500);
        concurrentBus.publishEvent(1101);
        concurrentBus.publishEvent(1200);
        concurrentBus.publishEvent(1300);
        concurrentBus.publishEvent(1401);
        concurrentBus.publishEvent(1500);

        assertTrue(cdLatch.await(10, TimeUnit.SECONDS));
        assertEquals(List.of(4001, 1101, 1401), values);

        concurrentBus.stop();
    }
}
