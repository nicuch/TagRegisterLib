package ro.nicuch.tag.nbt.async;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.function.BiFunction;
import java.util.function.Function;

public class AsyncInt2ObjectHashMap<V> implements AsyncInt2ObjectMap<V> {
    private final Int2ObjectMap<V> map;
    private final ExecutorService executors;
    private final ReentrantReadWriteLock lock;
    private final ReadLock readLock;
    private final WriteLock writeLock;

    public AsyncInt2ObjectHashMap() {
        this(Hash.DEFAULT_INITIAL_SIZE, Hash.DEFAULT_LOAD_FACTOR, null, false);
    }

    public AsyncInt2ObjectHashMap(int capacity) {
        this(capacity, Hash.DEFAULT_LOAD_FACTOR, null, false);
    }

    public AsyncInt2ObjectHashMap(int capacity, float loadFactor) {
        this(capacity, loadFactor, null, false);
    }

    public AsyncInt2ObjectHashMap(int capacity, float loadFactor, ExecutorService executors, boolean fairLock) {
        this.map = new Int2ObjectOpenHashMap<>(capacity, loadFactor);
        this.executors = executors != null ? executors : Executors.newCachedThreadPool();
        this.lock = new ReentrantReadWriteLock(fairLock);
        this.readLock = this.lock.readLock();
        this.writeLock = this.lock.writeLock();
    }

    @Override
    public ReentrantReadWriteLock getLock() {
        return this.lock;
    }

    @Override
    public Future<Void> clear() {
        return this.executors.submit(() -> {
            this.writeLock.lock();
            try {
                this.map.clear();
                return null;
            } finally {
                this.writeLock.unlock();
            }
        });
    }

    @Override
    public Future<V> put(int key, V value) {
        return this.executors.submit(() -> {
            this.writeLock.lock();
            try {
                return this.map.put(key, value);
            } finally {
                this.writeLock.unlock();
            }
        });
    }

    @Override
    public Future<V> replace(int key, V value) {
        return this.executors.submit(() -> {
            this.writeLock.lock();
            try {
                return this.map.replace(key, value);
            } finally {
                this.writeLock.unlock();
            }
        });
    }

    @Override
    public Future<V> get(int key) {
        return this.executors.submit(() -> {
            this.readLock.lock();
            try {
                return this.map.get(key);
            } finally {
                this.readLock.unlock();
            }
        });
    }

    @Override
    public Future<Boolean> containsKey(int key) {
        return this.executors.submit(() -> {
            this.readLock.lock();
            try {
                return this.map.containsKey(key);
            } finally {
                this.readLock.unlock();
            }
        });
    }

    @Override
    public Future<Boolean> containsValue(V value) {
        return this.executors.submit(() -> {
            this.readLock.lock();
            try {
                return this.map.containsValue(value);
            } finally {
                this.readLock.unlock();
            }
        });
    }

    @Override
    public Future<V> remove(int key) {
        return this.executors.submit(() -> {
            this.writeLock.lock();
            try {
                return this.map.remove(key);
            } finally {
                this.writeLock.unlock();
            }
        });
    }

    @Override
    public Future<Boolean> remove(int key, V value) {
        return this.executors.submit(() -> {
            this.writeLock.lock();
            try {
                return this.map.remove(key, value);
            } finally {
                this.writeLock.unlock();
            }
        });
    }

    @Override
    public Future<Integer> size() {
        return this.executors.submit(() -> {
            this.readLock.lock();
            try {
                return this.map.size();
            } finally {
                this.readLock.unlock();
            }
        });
    }

    @Override
    public Future<V> compute(int key, BiFunction<? super Integer, ? super V, ? extends V> remappingFunction) {
        return this.executors.submit(() -> {
            // acquire both write and read locks
            this.writeLock.lock();
            this.readLock.lock();
            try {
                return this.map.compute(key, remappingFunction);
            } finally {
                this.writeLock.unlock();
                this.readLock.unlock();
            }
        });
    }

    @Override
    public Future<V> computeIfAbsent(int key, Int2ObjectFunction<V> mappingFunction) {
        return this.executors.submit(() -> {
            // acquire both write and read locks
            this.writeLock.lock();
            this.readLock.lock();
            try {
                return this.map.computeIfAbsent(key, mappingFunction);
            } finally {
                this.writeLock.unlock();
                this.readLock.unlock();
            }
        });
    }

    @Override
    public Future<V> computeIfPresent(int key, Int2ObjectFunction<V> remappingFunction) {
        return this.executors.submit(() -> {
            // acquire both write and read locks
            this.writeLock.lock();
            this.readLock.lock();
            try {
                return this.map.computeIfAbsent(key, remappingFunction);
            } finally {
                this.writeLock.unlock();
                this.readLock.unlock();
            }
        });
    }

    @Override
    public ObjectSet<Int2ObjectMap.Entry<V>> int2ObjectEntrySet() {
        return this.map.int2ObjectEntrySet();
    }

    @Override
    public Future<Void> fill(int position, int length, V value) {
        return this.executors.submit(() -> {
            this.writeLock.lock();
            try {
                int end = position + length;
                for (int n = position; n < end; n++)
                    this.map.put(n, value);
                return null;
            } finally {
                this.writeLock.unlock();
            }
        });
    }

    @Override
    public Future<Void> fill(int position, int length, Function<? super Integer, ? extends V> mappingFunction) {
        return this.executors.submit(() -> {
            this.writeLock.lock();
            try {
                int end = position + length;
                for (int n = position; n < end; n++) {
                    V value = mappingFunction.apply(n);
                    this.map.put(n, value);
                }
                return null;
            } finally {
                this.writeLock.unlock();
            }
        });
    }
}
