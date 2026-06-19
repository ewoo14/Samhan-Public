package com.samhanair.logis.partnerorder.mig8.client;

import java.util.UUID;

/** partner-service partnerId summary lookup 결과. */
public record PartnerMig8Summary(
        UUID partnerId,
        String partnerCode,
        String bizCode,
        String partnerName) {
}
