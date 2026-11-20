package com.sansa.service_order.model;

public enum Status {

    PENDING,
    CONFIRMED,
    CANCELLED,
    // The saga tried to restore the decreased stock but at least one compensation failed:
    // the inventory may be inconsistent and the order needs manual reconciliation.
    COMPENSATION_FAILED

}
