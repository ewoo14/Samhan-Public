package com.samhanair.logis.notification.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** #1224 거래처코드 연결 마이그레이션의 상태·근거·롤백 계약. */
class ChatRoomPartnerCodeLinkMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V11__link_chat_rooms_to_partner_codes.sql");

    @Test
    void migration_records_all_decisions_before_linking_rows() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("CREATE TABLE partner_chat_room_mapping_link_audit")
                .contains("prior_partner_code")
                .contains("matched_partner_name")
                .contains("match_method")
                .contains("decision_status")
                .contains("UPDATE partner_chat_room_mappings")
                .contains("UNLINKED_AMBIGUOUS")
                .contains("UNLINKED_UNMATCHED")
                .contains("ROLLBACK");
    }
}
