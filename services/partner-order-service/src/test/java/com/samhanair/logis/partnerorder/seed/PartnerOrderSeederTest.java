package com.samhanair.logis.partnerorder.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartnerOrderSeederTest {

    @Test
    void deterministicProductIdMatchesProductSeederUtf8NamespaceForKoreanModelName() {
        String modelName = "삼성 윈드프리 한글모델";

        UUID expected = UUID.nameUUIDFromBytes(
                ("samhan-seed:product:" + modelName).getBytes(StandardCharsets.UTF_8));

        assertThat(PartnerOrderSeeder.deterministicProductId(modelName)).isEqualTo(expected);
    }

    @Test
    void deterministicProductIdUsesExplicitUtf8Bytes() throws IOException {
        String source = Files.readString(partnerOrderSeederSource(), StandardCharsets.UTF_8);

        assertThat(source)
                .contains("return UUID.nameUUIDFromBytes((\"samhan-seed:product:\" + modelName)"
                        + ".getBytes(StandardCharsets.UTF_8));");
    }

    private static Path partnerOrderSeederSource() {
        Path serviceRelative = Path.of(
                "src/main/java/com/samhanair/logis/partnerorder/seed/PartnerOrderSeeder.java");
        if (Files.exists(serviceRelative)) {
            return serviceRelative;
        }
        return Path.of(
                "services/partner-order-service/src/main/java/com/samhanair/logis/partnerorder/seed/PartnerOrderSeeder.java");
    }
}
