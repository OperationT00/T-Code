package com.tcode.runtime.api;

import com.tcode.hitl.ApprovalRequest;
import com.tcode.hitl.ApprovalResult;
import com.tcode.hitl.HitlHandler;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

public final class RuntimeHitlHandler implements HitlHandler {
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<String, PendingApproval> pending = new ConcurrentHashMap<>();
    private volatile boolean enabled = true;

    @Override
    public ApprovalResult requestApproval(ApprovalRequest request) {
        String id = "approval_" + Long.toHexString(sequence.incrementAndGet());
        PendingApproval approval = new PendingApproval(id, request, new CompletableFuture<>());
        pending.put(id, approval);
        try {
            return approval.result().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ApprovalResult.reject("Runtime approval wait interrupted");
        } catch (ExecutionException e) {
            return ApprovalResult.reject("Runtime approval failed: " + e.getMessage());
        } finally {
            pending.remove(id);
        }
    }

    public List<PendingApproval> pendingApprovals() {
        return pending.values().stream()
                .sorted(Comparator.comparing(PendingApproval::id))
                .toList();
    }

    public boolean resolve(String id, ApprovalResult result) {
        PendingApproval approval = pending.get(id);
        return approval != null && approval.result().complete(result);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public record PendingApproval(
            String id,
            ApprovalRequest request,
            CompletableFuture<ApprovalResult> result
    ) {
    }
}
