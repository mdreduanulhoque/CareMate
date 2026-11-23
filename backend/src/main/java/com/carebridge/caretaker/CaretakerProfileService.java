package com.carebridge.caretaker;

import com.carebridge.caretaker.dto.CaretakerOwnProfileResponse;
import com.carebridge.caretaker.dto.PublicCertificationBadge;
import com.carebridge.caretaker.dto.UpdateCaretakerProfileRequest;
import com.carebridge.common.ForbiddenException;
import com.carebridge.storage.AvatarStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class CaretakerProfileService {

    private final CaretakerProfileRepository profileRepository;
    private final CaretakerCertificationRepository certificationRepository;
    private final AvatarStorageService avatarStorageService;

    public CaretakerProfileService(
            CaretakerProfileRepository profileRepository,
            CaretakerCertificationRepository certificationRepository,
            AvatarStorageService avatarStorageService
    ) {
        this.profileRepository = profileRepository;
        this.certificationRepository = certificationRepository;
        this.avatarStorageService = avatarStorageService;
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
        return toResponse(saved);
    }

    @Transactional
    public CaretakerOwnProfileResponse updateAvailability(Long caretakerUserId, boolean available) {
        CaretakerProfile profile = requireApprovedProfile(caretakerUserId);
        profile.setAvailable(available);
        CaretakerProfile saved = profileRepository.saveAndFlush(profile);
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
            deleteAfterCommit(previousUrl);
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

    private void deleteAfterCommit(String oldAvatarUrl) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    avatarStorageService.deleteIfManaged(oldAvatarUrl);
                }
            });
        } else {
            avatarStorageService.deleteIfManaged(oldAvatarUrl);
        }
    }
}
