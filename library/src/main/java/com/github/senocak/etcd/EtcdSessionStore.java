package com.github.senocak.etcd;

import org.jspecify.annotations.Nullable;
import java.time.Duration;

/** Internal adapter that keeps the repository independent of Jetcd's asynchronous API. */
interface EtcdSessionStore {
    void put(String key, byte[] value, @Nullable Duration ttl);

    byte @Nullable [] get(String key);

    boolean contains(String key);

    void delete(String key);
}
