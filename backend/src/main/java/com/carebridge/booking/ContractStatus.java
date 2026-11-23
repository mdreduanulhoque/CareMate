package com.carebridge.booking;

import java.util.EnumSet;
import java.util.Set;

public enum ContractStatus {
    REQUESTED,
    SCHEDULED,
    ACTIVE,
    DECLINED,
    CANCELLED,
    COMPLETED_PENDING_SETTLEMENT,
    CLOSED_PAID,
    PAYMENT_DISPUTED,
    DISPUTE_RESOLVED;

    public static final Set<ContractStatus> BLOCKING_STATUSES = EnumSet.of(SCHEDULED, ACTIVE);
}
