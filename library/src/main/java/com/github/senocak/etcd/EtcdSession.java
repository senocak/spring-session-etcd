package com.github.senocak.etcd;

import org.jspecify.annotations.NonNull;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A mutable Spring Session backed by a {@link MapSession}. Changes are written by
 * {@link EtcdSessionRepository#save(EtcdSession)}.
 */
public final class EtcdSession implements Session {
    private final MapSession delegate;
    private final Consumer<MapSession> writer;
    private final Consumer<String> deleter;
    private boolean newSession;
    private boolean dirty;
    private String persistedSessionId;

    EtcdSession(@NonNull final MapSession delegate,
                final boolean newSession,
                @NonNull final Consumer<MapSession> writer,
                @NonNull final Consumer<String> deleter) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.newSession = newSession;
        this.writer = Objects.requireNonNull(writer, "writer must not be null");
        this.deleter = Objects.requireNonNull(deleter, "deleter must not be null");
        this.persistedSessionId = delegate.getId();
        this.dirty = newSession;
    }

    @NonNull
    @Override
    public String getId() {
        return delegate.getId();
    }

    @NonNull
    @Override
    public String changeSessionId() {
        final String id = delegate.changeSessionId();
        this.dirty = true;
        return id;
    }

    @Override
    public <T> T getAttribute(final @NonNull String attributeName) {
        return delegate.getAttribute(attributeName);
    }

    @NonNull
    @Override
    public Set<String> getAttributeNames() {
        return delegate.getAttributeNames();
    }

    @Override
    public void setAttribute(final @NonNull String attributeName, final @NonNull Object attributeValue) {
        delegate.setAttribute(attributeName, attributeValue);
        this.dirty = true;
    }

    @Override
    public void removeAttribute(final @NonNull String attributeName) {
        delegate.removeAttribute(attributeName);
        this.dirty = true;
    }

    @NonNull
    @Override
    public Instant getCreationTime() {
        return delegate.getCreationTime();
    }

    @Override
    public void setLastAccessedTime(final @NonNull Instant lastAccessedTime) {
        delegate.setLastAccessedTime(lastAccessedTime);
        this.dirty = true;
    }

    @NonNull
    @Override
    public Instant getLastAccessedTime() {
        return delegate.getLastAccessedTime();
    }

    @Override
    public void setMaxInactiveInterval(final @NonNull Duration interval) {
        delegate.setMaxInactiveInterval(interval);
        this.dirty = true;
    }

    @NonNull
    @Override
    public Duration getMaxInactiveInterval() {
        return delegate.getMaxInactiveInterval();
    }

    @Override
    public boolean isExpired() {
        return delegate.isExpired();
    }

    boolean isNewSession() {
        return newSession;
    }

    String idToCheckForSave() {
        return hasChangedSessionId() ? persistedSessionId : getId();
    }

    void persist() {
        if (!newSession && !dirty && !hasChangedSessionId()) {
            return;
        }
        if (hasChangedSessionId()) {
            deleter.accept(persistedSessionId);
        }
        writer.accept(delegate);
        persistedSessionId = getId();
        newSession = false;
        dirty = false;
    }

    private boolean hasChangedSessionId() {
        return !getId().equals(persistedSessionId);
    }
}
