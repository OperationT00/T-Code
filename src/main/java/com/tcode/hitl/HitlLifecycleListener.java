package com.tcode.hitl;

public interface HitlLifecycleListener {
    HitlLifecycleListener NO_OP = new HitlLifecycleListener() {
        @Override
        public void onRequested(ApprovalRequest request) {
        }

        @Override
        public void onResolved(ApprovalRequest request, ApprovalResult result) {
        }
    };

    void onRequested(ApprovalRequest request);

    void onResolved(ApprovalRequest request, ApprovalResult result);
}
