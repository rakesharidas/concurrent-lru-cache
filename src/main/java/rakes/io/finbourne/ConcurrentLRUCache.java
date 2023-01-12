package rakes.io.finbourne;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/***
 * ConcurrentLRUCache is a concurrent least recently used cache implementation of {@link  Cache}
 * Cache is limited by a max size. If the size exceeds, the least recently used item will be evicted.
 * Operations are thread safe. To increase the concurrency a read-write lock is used.
 * In order to ensure high performance, every action are done with a time complexity of O(1).
 * This includes identifying the item to evict.
 * @param <K> - Key type
 * @param <V> - Value type.
 */
public class ConcurrentLRUCache<K,V> implements Cache<K,V> {

    private final Map<K,Node<K,V>> map = new HashMap<>();

    private final LinkedNode<K,V> linkedNode = new LinkedNode<>();


    private final int maxSize;

    /**
     * Read-write lock to ensure bertter concurrency than a normal reenterant lock / synchronized key word.
     * The read lock won't stop another thread to read the data, though itn stops another write.
     * the write lock will block other write and read.
     */
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.readLock();

    private ConcurrentLRUCache(int maxSize) {
        this.maxSize = maxSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int size(){
        return map.size();
    }

    private static ConcurrentLRUCache cache;

    /**
     * Singleton method to create a new cache with max size specified.
     * @param maxSize - maximum size of cache. If exceed, the LRU eviction will take place.
     * @return - new Cache instance if not created yet, else the exiting one.
     */
    public static synchronized <K,V> ConcurrentLRUCache<K,V> create(int maxSize){
        if(maxSize<=0){
            throw new IllegalArgumentException("Maximum size of cache should be minimum 1");
        }
        if(cache==null){
            cache = new ConcurrentLRUCache<>(maxSize);
        }
        return cache;
    }

    /**
     * Invalidate the cache
     */
    public static synchronized void invalidate(){
        cache.clear();
        cache = null;
    }

    /**
     * {@inheritDoc}
     *  - Every put will check for the size and if the max size reached, then eviction kick starts.
     */
    public void put(K key, V value){
        Objects.requireNonNull(key, "key shouldn't be null");
        Objects.requireNonNull(value, "Value shouldn't be null");
        writeLock.lock();
        try {
            if(map.size() == maxSize){ //if the size has reached then - remove the tail value from LinkedNode
                Node<K,V> lastNode =  linkedNode.removeLastNode();
                if(lastNode!=null){
                    map.remove(lastNode.key);
                    // Time being logging this information, but can be enhanced to publish an event to listeners.
                    System.out.println("Evicting node :  " +  lastNode);
                }
            }if(map.containsKey(key)){
                // change the value and add the node to top of LinkedNode.
                Node<K,V> existingNode = map.get(key);
                Node<K,V> newNode = new Node<>(key, value, existingNode.prev, existingNode.next);
                linkedNode.removeNode(existingNode);
                linkedNode.addFirstNode(newNode);
            }else{
                Node<K,V> newNode = new Node<>(key, value);
                map.put(key, newNode);
                linkedNode.addFirstNode(newNode);
            }
        }finally {
            writeLock.unlock();
        }


    }

    /**
     * {@inheritDoc}
     * Get will promote the node to top of the linked list.
     */
    public Optional<V> get(K key){
        Objects.requireNonNull(key, "key shouldn't be null");
        readLock.lock();
        try{
            Node<K,V> node = map.get(key);
            if(node!= null){
                linkedNode.moveToTop(node);
                return Optional.ofNullable(node.value);
            }else{
                return Optional.empty();
            }
        }finally {
            readLock.unlock();
        }

    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Optional<V> remove(K key){
        Objects.requireNonNull(key, "key shouldn't be null");
        writeLock.lock();
        try{
            Node<K,V> node = map.get(key);
            if(node != null){
                linkedNode.removeNode(node);
                map.remove(key);
                return Optional.ofNullable(node.value);
            }
            return Optional.empty();
        }finally {
            writeLock.unlock();
        }
    }

    /**
     *
     * {@inheritDoc}
     * though contains check the element. The implementation doesn't change the rank of the element.
     */
    public boolean contains(K key){
        Objects.requireNonNull(key, "key shouldn't be null");
        readLock.lock();
        try {
            return map.containsKey(key);
        }finally {
            readLock.unlock();
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        map.clear();
        linkedNode.reset();
    }

    /**
     * Node store the Key and Value of the element.
     * It also store the previous and next element so that it is easy to navigate in both direction.
     * This also make linking or de-linking to a connected node with O(1) time complexity.
     * @param <K> - Key type
     * @param <V> - Value type.
     */
    private static class Node<K, V>{
        private final K key;
        private final V value;
        private Node<K,V> next;
        private Node<K, V> prev;

        private boolean lastNode = false;
        private Node(K key, V value){
            this(key, value, null, null);
        }
        private Node(K key, V value, Node<K,V> prev, Node<K, V> next){
            this.key  = key;
            this.value = value;
            this.prev = prev;
            this.next = next;
        }

        @Override
        public String toString() {
            return "Node{" +
                    "key=" + key +
                    ", value=" + value +
                    '}';
        }
    }

    /**
     * A doubly linked list to store nodes.
     * @param <K> - Key type
     * @param <V> - Value type.
     */
    private static class LinkedNode<K,V>{
        private Node<K,V> first;
        private Node<K,V> last;


        /**
         * Add node to the top of the list as first element.
         */
        void addFirstNode(Node<K,V> node){
            node.prev= null;
            node.next = first;
            if(first!=null){
                first.prev = node;
            }else{ // There isn't any node in LinkedNode.
                markNodeAsLast(node);
            }
            first = node;
        }

        void removeNode(Node<K,V> node){
            if(isLastNode(node)){
                removeLastNode();
            }else{
                removeMidNode(node);
            }
        }
        Node<K,V> removeLastNode(){
            Node<K,V> lastNode = last;
            if(lastNode!=null){
                Node<K,V> newLastNode = lastNode.prev;
                markNodeAsLast(newLastNode);
                deLinkNode(lastNode);
            }
            return lastNode;
        }

        void removeMidNode(Node<K,V> node){
            Node<K,V> prevNode = node.prev;
            Node<K,V> nextNode = node.next;
            if(prevNode !=null){
                prevNode.next = nextNode;
            }
            if(nextNode != null){
                nextNode.prev = prevNode;
            }
            deLinkNode(node);
        }

        void moveToTop(Node<K,V> node){
            removeNode(node);
            addFirstNode(node);
        }

        private void markNodeAsLast(Node<K,V> node){
            this.last = node;
            if(node!=null) {
                node.next = null;
                node.lastNode = true;
            }
        }

        private void deLinkNode(Node<K,V> node) {
            node.next = null;
            node.prev = null;
        }

        private boolean isLastNode(Node<K,V> node){
            return node.lastNode;
        }

        private void reset(){
            this.first  = null;
            this.last = null;
        }
    }
}
