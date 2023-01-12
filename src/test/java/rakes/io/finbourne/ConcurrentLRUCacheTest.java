package rakes.io.finbourne;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class ConcurrentLRUCacheTest {

    @Test
    void testPut() {
        Cache<String, String> concurrentLruCache = ConcurrentLRUCache.create(3);
        concurrentLruCache.put("a", "abc");
        concurrentLruCache.put("b", "bbc");
        concurrentLruCache.put("c", "cbc");
        concurrentLruCache.put("d", "dbc");
        assertEquals(3, concurrentLruCache.getMaxSize());
        assertEquals(3, concurrentLruCache.size());

        // LRU key is a , which shouldn't exist.
        assertFalse(concurrentLruCache.contains("a"));
        assertTrue(concurrentLruCache.contains("d"));
        assertTrue(concurrentLruCache.contains("b"));
        assertTrue(concurrentLruCache.contains("c"));

        // b is the new lru, but put a different value that should keep d still in cache.
        concurrentLruCache.put("b", "bbc-x");
        concurrentLruCache.put("a", "abc"); // a is back too.
        assertTrue(concurrentLruCache.contains("b"));
        assertTrue(concurrentLruCache.contains("a"));
        assertEquals("bbc-x", concurrentLruCache.get("b").orElse("invalid"));

    }

    @Test
    void testPutAndGet() {
        Cache<String, String> concurrentLruCache = ConcurrentLRUCache.create(3);
        concurrentLruCache.put("a", "abc");
        concurrentLruCache.put("b", "bbc");
        concurrentLruCache.put("c", "cbc");
        Optional<String> aVal = concurrentLruCache.get("a");
        concurrentLruCache.put("d", "dbc");


        // LRU key is b, as a was accessed later. which shouldn't exist.
        assertFalse(concurrentLruCache.contains("b"));
        assertTrue(concurrentLruCache.contains("a"));
        assertTrue(concurrentLruCache.contains("d"));
        assertTrue(concurrentLruCache.contains("c"));
        assertEquals("abc", aVal.orElse("invalid"));

        //put b back ,this time c shouldn't exist
        concurrentLruCache.put("b", "bbc");
        assertTrue(concurrentLruCache.contains("b"));
        assertFalse(concurrentLruCache.contains("c"));
    }

    @Test
    void testEdgeCaseWithSize1() {
        assertThrows(IllegalArgumentException.class, () -> ConcurrentLRUCache.create(0));
        Cache<Integer, String> cache = ConcurrentLRUCache.create(1);
        cache.put(1, "abc");
        cache.put(2, "abc");
        assertFalse(cache.contains(1));
        assertTrue(cache.contains(2));
    }

    @Test
    void testRemove() {
        Cache<Integer, String> cache = ConcurrentLRUCache.create(3);
        cache.put(1, "abc");
        cache.put(2, "bbc");
        cache.put(3, "cbc");
        Optional<String> removed = cache.remove(2);
        cache.put(4, "dbc");
        assertEquals(3, cache.size());
        assertEquals("bbc", removed.orElse("Invalid"));
        //Make sure the lru element 1, still exist in cache.
        assertTrue(cache.contains(1));
        assertFalse(cache.contains(2));

        //remove the LRU element and add the removed element -  test again.
        cache.remove(1);
        assertEquals(2, cache.size());
        cache.put(2, "ebc");
        assertFalse(cache.contains(1));
        assertTrue(cache.contains(2));

    }

    @Test
    void testClear() {
        Cache<Integer, String> cache = ConcurrentLRUCache.create(3);
        cache.put(1, "abc");
        cache.put(2, "bbc");
        cache.put(3, "cbc");

        cache.clear();
        assertEquals(0, cache.size());

        cache.put(1, "abc");
        cache.put(2, "bbc");
        assertEquals(2, cache.size());
        cache.put(3, "cbc");
        cache.put(4, "dbc");
        assertFalse(cache.contains(1));
        assertTrue(cache.contains(2));

    }

    /**
     * A simple test to make sure thread safety.
     * @throws InterruptedException
     */
    @Test
    void testParallelAccess() throws InterruptedException {
        Cache<Integer, Integer> cache = ConcurrentLRUCache.create(4);
        ExecutorService service = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(9);
        service.invokeAll(parallelTasks(cache, latch));
        latch.await();
        assertEquals(4, cache.size()); // Make sure the size haven't exceeded the max.
        service.shutdown();
    }

    private List<Callable<Void>> parallelTasks(Cache<Integer, Integer> cache, CountDownLatch latch) {
        List<Callable<Void>> tasks = new ArrayList<>();

        // create 3 tasks, each will put data to cache in parallel.
        for (int i = 0; i < 3; i++) {
            int start = i * 3;
            int end = (i + 1) * 3;
            Callable<Void> callable = () -> {
                IntStream.range(start, end)
                        .forEach(num -> {
                            cache.put(num, num);
                            latch.countDown();
                        });
                return null;
            };
            tasks.add(callable);
        }
        return tasks;
    }

    @AfterEach
    void afterEach() {
        ConcurrentLRUCache.invalidate();
    }


}
