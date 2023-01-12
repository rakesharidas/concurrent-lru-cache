## Requisites

 - Java 17, maven 3.8+
 - It may work with lower versions too, but haven't tested with lower versions.

### Building and running

 - mvn clean install to build and run the tests.
 - load to the IDE of your choice and run the tests under test module
 - There is only one test class - ConcurrentLRUCacheTest

### Main components - 
 - Interface   - Cache
 - Implementation - ConcurrentLRUCache
 - Test ConcurrentLRUCacheTest