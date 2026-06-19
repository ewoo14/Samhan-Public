package com.samhanair.logis.partnerorder.mig8.client;

import java.util.List;

/** MIG-8 export page. Spring Page JSON 중 이식에 필요한 content/last 만 보존한다. */
public record Mig8OrderPage(List<Mig8OrderExport> content, boolean last) {

    public Mig8OrderPage {
        content = content == null ? List.of() : List.copyOf(content);
    }

    public static Mig8OrderPage empty(boolean last) {
        return new Mig8OrderPage(List.of(), last);
    }
}
