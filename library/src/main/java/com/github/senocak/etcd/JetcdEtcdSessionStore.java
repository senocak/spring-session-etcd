package com.github.senocak.etcd;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.options.PutOption;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

final class JetcdEtcdSessionStore implements EtcdSessionStore {
    private final KV keyValueClient;
    private final Lease leaseClient;

    JetcdEtcdSessionStore(final Client client) {
        this(client.getKVClient(), client.getLeaseClient());
    }

    JetcdEtcdSessionStore(final KV keyValueClient, final Lease leaseClient) {
        this.keyValueClient = keyValueClient;
        this.leaseClient = leaseClient;
    }

    @Override
    public void put(final String key, final byte[] value, @Nullable final Duration ttl) {
        final ByteSequence etcdKey = bytes(key);
        if (ttl == null) {
            await(keyValueClient.put(etcdKey, ByteSequence.from(value)), "write session");
            return;
        }
        final long leaseId = await(leaseClient.grant(ttl.toSeconds()), "create session lease").getID();
        final PutOption option = PutOption.newBuilder().withLeaseId(leaseId).build();
        await(keyValueClient.put(etcdKey, ByteSequence.from(value), option), "write leased session");
    }

    @Override
    public byte @Nullable [] get(final String key) {
        final var keyValues = await(keyValueClient.get(bytes(key)), "read session").getKvs();
        return keyValues.isEmpty() ? null : keyValues.getFirst().getValue().getBytes();
    }

    @Override
    public boolean contains(final String key) {
        return !await(keyValueClient.get(bytes(key)), "check session").getKvs().isEmpty();
    }

    @Override
    public void delete(final String key) {
        await(keyValueClient.delete(bytes(key)), "delete session");
    }

    private static ByteSequence bytes(final String value) {
        return ByteSequence.from(value, StandardCharsets.UTF_8);
    }

    private static <T> T await(final @NonNull CompletableFuture<T> operation, final String action) {
        try {
            return operation.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while attempting to " + action + " in etcd", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Unable to " + action + " in etcd", ex.getCause());
        }
    }
}
