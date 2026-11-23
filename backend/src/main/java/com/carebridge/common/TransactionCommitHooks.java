package com.carebridge.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ensures side effects (async tasks, WebSocket broadcasts, file cleanup) execute only after DB commit.
 */
public final class TransactionCommitHooks {

    private TransactionCommitHooks() {
    }

    public static void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
