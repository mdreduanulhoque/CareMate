package com.carebridge.user;

import com.carebridge.booking.HiringTransactionalService;
import com.carebridge.caretaker.CaretakerProfile;
import com.carebridge.caretaker.CaretakerProfileRepository;
import com.carebridge.common.BadRequestException;
import com.carebridge.common.NotFoundException;
import com.carebridge.common.TransactionCommitHooks;
import com.carebridge.notification.RealtimeEventDispatcher;
import com.carebridge.notification.dto.FeedAvailabilityEvent;
import com.carebridge.user.dto.AdminUserSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final CaretakerProfileRepository caretakerProfileRepository;
    private final HiringTransactionalService hiringTransactionalService;
    private final RealtimeEventDispatcher realtimeEventDispatcher;

    public AdminUserService(
            UserRepository userRepository,
            CaretakerProfileRepository caretakerProfileRepository,
            HiringTransactionalService hiringTransactionalService,
            RealtimeEventDispatcher realtimeEventDispatcher
    ) {
        this.userRepository = userRepository;
        this.caretakerProfileRepository = caretakerProfileRepository;
        this.hiringTransactionalService = hiringTransactionalService;
        this.realtimeEventDispatcher = realtimeEventDispatcher;
    }

    @Transactional(readOnly = true)
    public List<AdminUserSummaryResponse> listUsers(Role roleFilter, String keyword) {
        String loweredKeyword = keyword != null && !keyword.isBlank()
                ? keyword.trim().toLowerCase(Locale.ROOT)
                : null;

        List<User> filteredUsers = userRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .filter(user -> roleFilter == null || user.getRole() == roleFilter)
                .filter(user -> matchesKeyword(user, loweredKeyword))
                .toList();

        List<Long> caretakerIds = filteredUsers.stream()
                .filter(u -> u.getRole() == Role.CARETAKER)
                .map(User::getId)
                .toList();

        Map<Long, CaretakerProfile> profileByUserId = caretakerIds.isEmpty()
                ? Collections.emptyMap()
                : caretakerProfileRepository.findByUserIdIn(caretakerIds).stream()
                        .collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity(), (a, b) -> a));

        return filteredUsers.stream()
                .map(user -> AdminUserSummaryResponse.from(user, profileByUserId.get(user.getId())))
                .toList();
    }

    @Transactional
    public AdminUserSummaryResponse updateUserActiveStatus(
            Long actingAdminId,
            Long targetUserId,
            boolean active
    ) {
        if (actingAdminId.equals(targetUserId)) {
            throw new BadRequestException("You cannot deactivate your own Admin account.");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("User account not found."));
        if (target.getRole() == Role.ADMIN) {
            throw new BadRequestException("Admin accounts cannot be modified through this moderation endpoint.");
        }

        target.setActive(active);
        User saved = userRepository.saveAndFlush(target);
        CaretakerProfile profile = saved.getRole() == Role.CARETAKER
                ? caretakerProfileRepository.findByUserId(saved.getId()).orElse(null)
                : null;
        return AdminUserSummaryResponse.from(saved, profile);
    }

    @Transactional
    public AdminUserSummaryResponse moderateCaretakerAvailability(Long caretakerUserId, boolean available) {
        CaretakerProfile profile = caretakerProfileRepository.findByUserIdWithUser(caretakerUserId)
                .orElseThrow(() -> new NotFoundException("Approved caretaker profile not found."));
        profile.setAvailable(available);
        CaretakerProfile saved = caretakerProfileRepository.saveAndFlush(profile);

        int todayCount = hiringTransactionalService.countTodayBlockingBookings(caretakerUserId);
        FeedAvailabilityEvent event = FeedAvailabilityEvent.from(saved, todayCount);
        TransactionCommitHooks.runAfterCommit(() -> realtimeEventDispatcher.publishFeedAvailability(event));

        return AdminUserSummaryResponse.from(saved.getUser(), saved);
    }

    private boolean matchesKeyword(User user, String loweredKeyword) {
        if (loweredKeyword == null) {
            return true;
        }
        return user.getFullName().toLowerCase(Locale.ROOT).contains(loweredKeyword)
                || user.getMobileNumber().toLowerCase(Locale.ROOT).contains(loweredKeyword)
                || user.getAddress().toLowerCase(Locale.ROOT).contains(loweredKeyword)
                || (user.getEmail() != null && user.getEmail().toLowerCase(Locale.ROOT).contains(loweredKeyword));
    }
}
