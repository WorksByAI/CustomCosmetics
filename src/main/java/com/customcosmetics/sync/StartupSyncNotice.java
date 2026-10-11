package com.customcosmetics.sync;

import java.util.EnumMap;
import java.util.EnumSet;

/** One result from the first automatic read and write on each game connection. */
public final class StartupSyncNotice {
    public enum Operation { READ, WRITE }
    public record Ticket(long generation, Operation operation) {}
    public record Result(boolean success, SyncFailure failure) {
        public Result(boolean success) { this(success, success ? null : SyncFailure.of(SyncFailure.Reason.UNKNOWN, SyncFailure.Stage.OTHER)); }
    }
    private static final long DELAY_MS = 3_000;
    private final EnumSet<Operation> started = EnumSet.noneOf(Operation.class);
    private final EnumMap<Operation, Boolean> results = new EnumMap<>(Operation.class);
    private long generation, completedAt;
    private boolean inWorld, delivered;
    private Boolean pendingResult;
    private SyncFailure pendingFailure;

    public synchronized void enterWorld() {
        generation++;
        inWorld = true;
        delivered = false;
        pendingResult = null;
        pendingFailure = null;
        started.clear();
        results.clear();
    }

    public synchronized void leaveWorld() {
        generation++;
        inWorld = false;
        pendingResult = null;
    }

    public synchronized void addressChanged() {
        generation++;
        if (!delivered && pendingResult == null) {
            started.clear();
            results.clear();
        }
    }

    public synchronized Ticket beginAutomatic(Operation operation) {
        if (!inWorld || delivered || pendingResult != null || !started.add(operation)) return null;
        return new Ticket(generation, operation);
    }

    public synchronized void complete(Ticket ticket, boolean success, long now) {
        complete(ticket, success, null, now);
    }

    public synchronized void complete(Ticket ticket, boolean success, SyncFailure failure, long now) {
        if (ticket == null || !inWorld || delivered || pendingResult != null || ticket.generation() != generation
                || results.putIfAbsent(ticket.operation(), success) != null) return;
        if (!success || results.size() == Operation.values().length) {
            pendingResult = success;
            pendingFailure = success ? null : failure == null ? SyncFailure.of(SyncFailure.Reason.UNKNOWN, SyncFailure.Stage.OTHER) : failure;
            completedAt = now;
        }
    }

    public synchronized Result poll(long now) {
        if (!inWorld || delivered || pendingResult == null || now - completedAt < DELAY_MS) return null;
        delivered = true;
        return new Result(pendingResult, pendingFailure);
    }
}
