package com.samhanair.logis.partnerorder.client;

/** product-service UsageScope mirror — order-app bootstrap 는 PARTNER_ORDER/BOTH 노출만 읽는다. */
public enum UsageScope {
    NONE,
    ESTIMATE,
    PARTNER_ORDER,
    BOTH
}
