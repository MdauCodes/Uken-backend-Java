package com.mdau.ukena.payment;

public enum LedgerStatus {
    PENDING,
    INCLUDED_IN_PAYOUT,
    PAID,
    /** A refund reversed this entry before it was ever paid out — kept, not deleted,
     *  so the ledger stays a real audit trail. Never used for an entry that was
     *  already paid out to a creator (see OrderService.adminRefund). */
    REVERSED
}
