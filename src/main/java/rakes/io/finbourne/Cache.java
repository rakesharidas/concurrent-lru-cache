package rakes.io.finbourne;

import java.util.Optional;

public interface Cache <K,V>{
    /**
     * Put a new Key value pair to cache.
     * @param key  - Key - can be any object. However, the performance of the cache will depend on a
     *             good equals, hashcode implementation
     * @param value - value - Any object.
     */
    void put(K key, V value);

    /**
     *  To get the value using key in a null-safe way.
     * @param key - key
     * @return - Optional value , empty if the key value pair doesn't exist.
     */
    Optional<V> get(K key);

    /**
     * Remove an item from cache using key.
     * @param key - key
     * @return - Optional value , empty if the key value pair doesn't exist.
     */
    Optional<V> remove(K key);

    /**
     * To check whether an object exist in cache using key.
     * @param key - key.
     */
    boolean contains(K key);

    /**
     * @return - Get nax size of cache.
     */
    int getMaxSize();

    /**
     * @return - current size of cache.
     */
    int size();

    /**
     * Clear cache data.
     */
    void clear();
}
