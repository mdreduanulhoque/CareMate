package com.carebridge.caretaker;

import com.carebridge.caretaker.dto.CaretakerOwnProfileResponse;
import com.carebridge.caretaker.dto.PublicCertificationBadge;
import com.carebridge.caretaker.dto.UpdateCaretakerProfileRequest;
import com.carebridge.common.ForbiddenException;
import com.carebridge.common.TransactionCommitHooks;
import com.carebridge.notification.RealtimeEventDispatcher;
import com.carebridge.notification.dto.FeedAvailabilityEvent;
import com.carebridge.storage.AvatarStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class CaretakerProfileService {

    private final CaretakerProfileRepository profileRepository;
    private final CaretakerCertificationRepository certificationRepository;
    private final AvatarStorageService avatarStorageService;
    private final RealtimeEventDispatcher realtimeEventDispatcher;

    public CaretakerProfileService(
            CaretakerProfileRepository profileRepository,
            CaretakerCertificationRepository certificationRepository,
            AvatarStorageService avatarStorageService,
            RealtimeEventDispatcher realtimeEventDispatcher
    ) {
        this.profileRepository = profileRepository;
        this.certificationRepository = certificationRepository;
        this.avatarStorageService = avatarStorageService;
        this.realtimeEventDispatcher = realtimeEventDispatcher;
    }

    @Transactional(readOnly = true)
    public CaretakerOwnProfileResponse getMyProfile(Long caretakerUserId) {
        CaretakerProfile profile = requireApprovedProfile(caretakerUserId);
        return toResponse(profile);
    }

    @Transactional
    public CaretakerOwnProfileResponse updateMyProfile(
            Long caretakerUserId,
            UpdateCaretakerProfileRequest request
    ) {
        CaretakerProfile profile = requireApprovedProfile(caretakerUserId);
        profile.setBio(normalizeOptionalBio(request.bio()));
        profile.setLocationArea(request.locationArea().trim());
        profile.setRateAmount(request.rateAmount());
        profile.setRateType(request.rateType());
        profile.setAvailable(request.available());

        CaretakerProfile saved = profileRepository.saveAndFlush(profile);
        FeedAvailabilityEvent event = FeedAvailabilityEvent.from(saved);
        TransactionCommitHooks.runAfterCommit(() -> realtimeEventDispatcher.publishFeedAvailability(event));
        return toResponse(saved);
    }

    @Transactional
    public CaretakerOwnProfileResponse updateAvailability(Long caretakerUserId, boolean available) {
        CaretakerProfile profile = requireApprovedProfile(caretakerUserId);
        profile.setAvailable(available);
        CaretakerProfile saved = profileRepository.saveAndFlush(profile);
        FeedAvailabilityEvent event = FeedAvailabilityEvent.from(saved);
        TransactionCommitHooks.runAfterCommit(() -> realtimeEventDispatcher.publishFeedAvailability(event));
        return toResponse(saved);
    }

    @Transactional
    public CaretakerOwnProfileResponse uploadAvatar(Long caretakerUserId, MultipartFile file) {
        CaretakerProfile profile = requireApprovedProfile(caretakerUserId);
        String previousUrl = profile.getProfileImageUrl();
        String storedUrl = avatarStorageService.storeAvatar(file);

        profile.setProfileImageUrl(storedUrl);
        CaretakerProfile saved = profileRepository.saveAndFlush(profile);

        if (previousUrl != null && !previousUrl.equals(storedUrl)) {
            TransactionCommitHooks.runAfterCommit(() -> avatarStorageService.deleteIfManaged(previousUrl));
        }

        return toResponse(saved);
    }

    private CaretakerProfile requireApprovedProfile(Long caretakerUserId) {
        return profileRepository.findByUserIdWithUser(caretakerUserId)
                .orElseThrow(() -> new ForbiddenException(
                        "Your caretaker profile is locked until an Admin approves your verification application."
                ));
    }

    private CaretakerOwnProfileResponse toResponse(CaretakerProfile profile) {
        List<PublicCertificationBadge> verifiedCerts = certificationRepository
                .findByCaretakerProfileIdAndVerificationStatusOrderByReviewedAtDesc(
                        profile.getId(),
                        VerificationStatus.APPROVED
                )
                .stream()
                .map(PublicCertificationBadge::from)
                .toList();
        return CaretakerOwnProfileResponse.from(profile, verifiedCerts);
    }

    private String normalizeOptionalBio(String rawBio) {
        if (rawBio == null) {
            return null;
        }
        String trimmed = rawBio.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
