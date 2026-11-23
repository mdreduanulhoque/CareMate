package com.carebridge.chat;

import com.carebridge.common.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NegotiationRoomIdTest {

    @Test
    void formatsAndParsesValidDeterministicRoomId() {
        String roomId = NegotiationRoomId.format(12L, 45L);
        assertThat(roomId).isEqualTo("NEGOTIATION:12:45");

        NegotiationRoomId.Participants participants = NegotiationRoomId.parseOrThrow(roomId);
        assertThat(participants.customerId()).isEqualTo(12L);
        assertThat(participants.caretakerId()).isEqualTo(45L);
        assertThat(participants.canonicalRoomId()).isEqualTo("NEGOTIATION:12:45");
        assertThat(participants.counterpartFor(12L)).isEqualTo(45L);
        assertThat(participants.counterpartFor(45L)).isEqualTo(12L);
    }

    @Test
    void rejectsNonPositiveIdsWhenFormatting() {
        assertThatThrownBy(() -> NegotiationRoomId.format(0L, 5L))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> NegotiationRoomId.format(5L, -1L))
                .isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "NEGOTIATION:0:5",
            "NEGOTIATION:5:0",
            "NEGOTIATION:-1:5",
            "NEGOTIATION:12:45:99",
            "NEGOTIATION:abc:45",
            "DISPUTE:12",
            "NEGOTIATION:12/*",
            "NEGOTIATION:12/45",
            "NEGOTIATION:99999999999999999999999:1"
    })
    void rejectsMalformedOrUnsafeRoomIdentifiers(String rawRoomId) {
        assertThat(NegotiationRoomId.tryParse(rawRoomId)).isEmpty();
        assertThatThrownBy(() -> NegotiationRoomId.parseOrThrow(rawRoomId))
                .isInstanceOf(BadRequestException.class);
    }
}
