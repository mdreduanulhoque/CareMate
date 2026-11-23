package com.carebridge.chat;

import com.carebridge.common.BadRequestException;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic negotiation room identifier formatted as NEGOTIATION:{customerId}:{caretakerId}.
 */
public final class NegotiationRoomId {

    private static final String PREFIX = "NEGOTIATION:";
    private static final Pattern ROOM_PATTERN = Pattern.compile("^NEGOTIATION:([1-9]\\d{0,18}):([1-9]\\d{0,18})$");

    private NegotiationRoomId() {
    }

    public static String format(long customerId, long caretakerId) {
        if (customerId <= 0 || caretakerId <= 0) {
            throw new BadRequestException("Invalid room participant identifiers.");
        }
        return PREFIX + customerId + ":" + caretakerId;
    }

    public static Optional<Participants> tryParse(String rawRoomId) {
        if (rawRoomId == null) {
            return Optional.empty();
        }
        Matcher matcher = ROOM_PATTERN.matcher(rawRoomId.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            long customerId = Long.parseLong(matcher.group(1));
            long caretakerId = Long.parseLong(matcher.group(2));
            return Optional.of(new Participants(customerId, caretakerId));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public static Participants parseOrThrow(String rawRoomId) {
        return tryParse(rawRoomId)
                .orElseThrow(() -> new BadRequestException("Invalid negotiation chat room identifier."));
    }

    public record Participants(long customerId, long caretakerId) {
        public String canonicalRoomId() {
            return format(customerId, caretakerId);
        }

        public long counterpartFor(long actingUserId) {
            if (actingUserId == customerId) {
                return caretakerId;
            }
            if (actingUserId == caretakerId) {
                return customerId;
            }
            throw new IllegalArgumentException("User is not a participant of this negotiation room.");
        }
    }
}
