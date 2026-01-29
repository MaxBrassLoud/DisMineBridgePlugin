package de.MaxBrassLoud.disMineBridge.database;

public class ViolationRecord {
    public final int violationId;
    public final String reason;
    public final String punishmentType;
    public final String executor;
    public final long createdAt;

    public ViolationRecord(int violationId, String reason, String punishmentType,
                           String executor, long createdAt) {
        this.violationId = violationId;
        this.reason = reason;
        this.punishmentType = punishmentType;
        this.executor = executor;
        this.createdAt = createdAt;
    }
}
