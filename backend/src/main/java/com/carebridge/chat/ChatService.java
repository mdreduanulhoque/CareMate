package com.carebridge.chat;

import com.carebridge.booking.ContactUnlock;
import com.carebridge.booking.ContactUnlockRepository;
import com.carebridge.caretaker.CaretakerProfile;
import com.carebridge.caretaker.CaretakerProfileRepository;
import com.carebridge.chat.dto.ChatMessageResponse;
import com.carebridge.chat.dto.NegotiationRoomDetailResponse;
import com.carebridge.chat.dto.NegotiationRoomSummaryResponse;
import com.carebridge.common.BadRequestException;
import com.carebridge.common.ForbiddenException;
import com.carebridge.common.NotFoundException;
import com.carebridge.common.TransactionCommitHooks;
import com.carebridge.notification.RealtimeEventDispatcher;
import com.carebridge.notification.dto.UserNotificationEvent;
import com.carebridge.security.CareBridgeUserPrincipal;
import com.carebridge.user.Role;
import com.carebridge.user.User;
import com.carebridge.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int DEFAULT_HISTORY_LIMIT = 100;
    private static final int MAX_HISTORY_LIMIT = 200;
    private static final int PREVIEW_MAX_LENGTH = 100;
    private static final String TOPIC_NEGOTIATION_PREFIX = "/topic/chat/negotiation/";

    private final ContactUnlockRepository contactUnlockRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CaretakerProfileRepository caretakerProfileRepository;
    private final UserRepository userRepository;
    private final ChatRoomAuthorizationService chatRoomAuthorizationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RealtimeEventDispatcher realtimeEventDispatcher;

    public ChatService(
            ContactUnlockRepository contactUnlockRepository,
            ChatMessageRepository chatMessageRepository,
            CaretakerProfileRepository caretakerProfileRepository,
            UserRepository userRepository,
            ChatRoomAuthorizationService chatRoomAuthorizationService,
            SimpMessagingTemplate messagingTemplate,
            RealtimeEventDispatcher realtimeEventDispatcher
    ) {
        this.contactUnlockRepository = contactUnlockRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.caretakerProfileRepository = caretakerProfileRepository;
        this.userRepository = userRepository;
        this.chatRoomAuthorizationService = chatRoomAuthorizationService;
        this.messagingTemplate = messagingTemplate;
        this.realtimeEventDispatcher = realtimeEventDispatcher;
    }

    @Transactional(readOnly = true)
    public List<NegotiationRoomSummaryResponse> listNegotiationRooms(CareBridgeUserPrincipal requester) {
        if (requester.getRole() == Role.CUSTOMER) {
            List<ContactUnlock> unlocks = contactUnlockRepository.findAllForCustomer(requester.getUserId());
            return buildRoomSummaries(requester, unlocks);
        }

        if (requester.getRole() == Role.CARETAKER) {
            if (!caretakerProfileRepository.existsByUserId(requester.getUserId())) {
                throw new ForbiddenException(
                        "Negotiation chats unlock after an Admin approves your caretaker verification."
                );
            }
            List<ContactUnlock> unlocks = contactUnlockRepository.findAllForCaretaker(requester.getUserId());
            return buildRoomSummaries(requester, unlocks);
        }

        throw new ForbiddenException("Only customers and approved caretakers can access negotiation chats.");
    }

    @Transactional(readOnly = true)
    public NegotiationRoomDetailResponse getNegotiationRoomDetail(
            CareBridgeUserPrincipal requester,
            String rawRoomId,
            int limit
    ) {
        NegotiationRoomId.Participants participants =
                chatRoomAuthorizationService.authorizeNegotiationRoom(requester, rawRoomId);
        String canonicalRoomId = participants.canonicalRoomId();

        ContactUnlock unlock = contactUnlockRepository
                .findWithUsersByCustomerIdAndCaretakerId(participants.customerId(), participants.caretakerId())
                .orElseThrow(() -> new NotFoundException("Negotiation room not found."));

        CaretakerProfile caretakerProfile = caretakerProfileRepository
                .findByUserId(participants.caretakerId())
                .orElse(null);

        int safeLimit = Math.clamp(limit <= 0 ? DEFAULT_HISTORY_LIMIT : limit, 1, MAX_HISTORY_LIMIT);
        Page<ChatMessage> page = chatMessageRepository.findByRoomIdOrderBySentAtDescIdDesc(
                canonicalRoomId,
                PageRequest.of(0, safeLimit)
        );

        List<ChatMessage> chronological = new ArrayList<>(page.getContent());
        Collections.reverse(chronological);

        ChatMessage latest = chronological.isEmpty() ? null : chronological.getLast();
        NegotiationRoomSummaryResponse summary = toRoomSummary(requester, unlock, caretakerProfile, latest);
        List<ChatMessageResponse> messages = chronological.stream()
                .map(ChatMessageResponse::from)
                .toList();

        return new NegotiationRoomDetailResponse(summary, messages, page.hasNext());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getNegotiationMessages(
            CareBridgeUserPrincipal requester,
            String rawRoomId,
            int page,
            int size
    ) {
        NegotiationRoomId.Participants participants =
                chatRoomAuthorizationService.authorizeNegotiationRoom(requester, rawRoomId);

        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size <= 0 ? DEFAULT_HISTORY_LIMIT : size, 1, MAX_HISTORY_LIMIT);

        Page<ChatMessage> messagePage = chatMessageRepository.findByRoomIdOrderBySentAtDescIdDesc(
                participants.canonicalRoomId(),
                PageRequest.of(safePage, safeSize)
        );

        List<ChatMessage> chronological = new ArrayList<>(messagePage.getContent());
        Collections.reverse(chronological);
        return chronological.stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    @Transactional
    public ChatMessageResponse sendNegotiationMessage(
            CareBridgeUserPrincipal senderPrincipal,
            String rawRoomId,
            String rawContent
    ) {
        NegotiationRoomId.Participants participants =
                chatRoomAuthorizationService.authorizeNegotiationRoom(senderPrincipal, rawRoomId);
        String canonicalRoomId = participants.canonicalRoomId();
        String sanitizedContent = sanitizeMessageContent(rawContent);

        User sender = userRepository.findById(senderPrincipal.getUserId())
                .orElseThrow(() -> new NotFoundException("Sender account not found."));

        ChatMessage saved = chatMessageRepository.saveAndFlush(new ChatMessage(
                canonicalRoomId,
                ChatRoomType.NEGOTIATION,
                sender,
                null,
                sanitizedContent
        ));

        ChatMessageResponse response = ChatMessageResponse.from(saved);
        long counterpartUserId = participants.counterpartFor(sender.getId());
        String preview = buildPreview(sanitizedContent);

        TransactionCommitHooks.runAfterCommit(() -> {
            messagingTemplate.convertAndSend(TOPIC_NEGOTIATION_PREFIX + canonicalRoomId, response);
            realtimeEventDispatcher.publishUserNotification(
                    counterpartUserId,
                    UserNotificationEvent.of(
                            "NEW_CHAT_MESSAGE",
                            canonicalRoomId,
                            "New message from " + sender.getFullName(),
                            preview,
                            sender.getId(),
                            sender.getFullName()
                    )
            );
        });

        return response;
    }

    private List<NegotiationRoomSummaryResponse> buildRoomSummaries(
            CareBridgeUserPrincipal requester,
            List<ContactUnlock> unlocks
    ) {
        if (unlocks.isEmpty()) {
            return List.of();
        }

        List<String> roomIds = unlocks.stream()
                .map(u -> NegotiationRoomId.format(u.getCustomer().getId(), u.getCaretaker().getId()))
                .toList();

        Map<String, ChatMessage> latestByRoom = chatMessageRepository.findLatestMessagesByRoomIds(roomIds)
                .stream()
                .collect(Collectors.toMap(ChatMessage::getRoomId, Function.identity(), (a, b) -> a));

        Map<Long, CaretakerProfile> profileByCaretakerUserId = Collections.emptyMap();
        if (requester.getRole() == Role.CUSTOMER) {
            List<Long> caretakerIds = unlocks.stream()
                    .map(u -> u.getCaretaker().getId())
                    .distinct()
                    .toList();
            profileByCaretakerUserId = caretakerProfileRepository.findByUserIdIn(caretakerIds)
                    .stream()
                    .collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity(), (a, b) -> a));
        }

        Map<Long, CaretakerProfile> profilesLookup = profileByCaretakerUserId;
        return unlocks.stream()
                .map(unlock -> {
                    String roomId = NegotiationRoomId.format(
                            unlock.getCustomer().getId(),
                            unlock.getCaretaker().getId()
                    );
                    CaretakerProfile profile = profilesLookup.get(unlock.getCaretaker().getId());
                    return toRoomSummary(requester, unlock, profile, latestByRoom.get(roomId));
                })
                .sorted(Comparator.comparing(
                        summary -> summary.lastMessageAt() != null ? summary.lastMessageAt() : summary.unlockedAt(),
                        Comparator.reverseOrder()
                ))
                .toList();
    }

    private NegotiationRoomSummaryResponse toRoomSummary(
            CareBridgeUserPrincipal requester,
            ContactUnlock unlock,
            CaretakerProfile caretakerProfile,
            ChatMessage latestMessage
    ) {
        User customer = unlock.getCustomer();
        User caretaker = unlock.getCaretaker();
        String roomId = NegotiationRoomId.format(customer.getId(), caretaker.getId());

        boolean viewingAsCustomer = requester.getRole() == Role.CUSTOMER;
        User counterpart = viewingAsCustomer ? caretaker : customer;

        return new NegotiationRoomSummaryResponse(
                roomId,
                customer.getId(),
                caretaker.getId(),
                counterpart.getId(),
                counterpart.getFullName(),
                counterpart.getRole(),
                counterpart.getMobileNumber(),
                viewingAsCustomer && caretakerProfile != null ? caretakerProfile.getProfileImageUrl() : null,
                viewingAsCustomer && caretakerProfile != null ? caretakerProfile.getPrimaryQualification() : null,
                viewingAsCustomer && caretakerProfile != null
                        ? caretakerProfile.getLocationArea()
                        : counterpart.getAddress(),
                viewingAsCustomer && caretakerProfile != null ? caretakerProfile.isAvailable() : null,
                viewingAsCustomer && caretakerProfile != null ? caretakerProfile.getRateAmount() : null,
                viewingAsCustomer && caretakerProfile != null ? caretakerProfile.getRateType() : null,
                unlock.getUnlockedAt(),
                latestMessage != null ? latestMessage.getContent() : null,
                latestMessage != null ? latestMessage.getSender().getId() : null,
                latestMessage != null ? latestMessage.getSentAt() : null
        );
    }

    private String sanitizeMessageContent(String rawContent) {
        if (rawContent == null) {
            throw new BadRequestException("Message cannot be empty.");
        }
        String normalizedLineEndings = rawContent.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder cleaned = new StringBuilder(normalizedLineEndings.length());
        for (int i = 0; i < normalizedLineEndings.length(); i++) {
            char ch = normalizedLineEndings.charAt(i);
            if (ch == '\n' || ch == '\t' || (!Character.isISOControl(ch))) {
                cleaned.append(ch);
            }
        }
        String trimmed = cleaned.toString().trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("Message cannot be empty.");
        }
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            throw new BadRequestException("Message must be 2000 characters or fewer.");
        }
        return trimmed;
    }

    private String buildPreview(String content) {
        String singleLine = content.replace('\n', ' ').trim();
        if (singleLine.length() <= PREVIEW_MAX_LENGTH) {
            return singleLine;
        }
        return singleLine.substring(0, PREVIEW_MAX_LENGTH - 1) + "…";
    }
}
