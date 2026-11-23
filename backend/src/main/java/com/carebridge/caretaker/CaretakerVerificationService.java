package com.carebridge.caretaker;

import com.carebridge.caretaker.drive.AsyncDriveValidationService;
import com.carebridge.caretaker.drive.GoogleDriveUrlValidator;
import com.carebridge.caretaker.dto.CaretakerApplicationRequest;
import com.carebridge.caretaker.dto.CaretakerApplicationResponse;
import com.carebridge.caretaker.dto.CaretakerApplicationStatusResponse;
import com.carebridge.caretaker.dto.CertificationResponse;
import com.carebridge.caretaker.dto.NewCertificationRequest;
import com.carebridge.common.BadRequestException;
import com.carebridge.common.ConflictException;
import com.carebridge.common.ForbiddenException;
import com.carebridge.common.NotFoundException;
import com.carebridge.common.TransactionCommitHooks;
import com.carebridge.notification.RealtimeEventDispatcher;
import com.carebridge.notification.dto.AdminQueueAlertEvent;
import com.carebridge.user.Role;
import com.carebridge.user.User;
import com.carebridge.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class CaretakerVerificationService {

    private static final ZoneId DHAKA_ZONE = ZoneId.of("Asia/Dhaka");
    private static final int MIN_APPLICANT_AGE = 18;
    private static final int MAX_APPLICANT_AGE = 100;

    private final UserRepository userRepository;
    private final CaretakerApplicationRepository applicationRepository;
    private final CaretakerProfileRepository profileRepository;
    private final CaretakerCertificationRepository certificationRepository;
    private final AsyncDriveValidationService asyncDriveValidationService;
    private final RealtimeEventDispatcher realtimeEventDispatcher;

    public CaretakerVerificationService(
            UserRepository userRepository,
            CaretakerApplicationRepository applicationRepository,
            CaretakerProfileRepository profileRepository,
            CaretakerCertificationRepository certificationRepository,
            AsyncDriveValidationService asyncDriveValidationService,
            RealtimeEventDispatcher realtimeEventDispatcher
    ) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.profileRepository = profileRepository;
        this.certificationRepository = certificationRepository;
        this.asyncDriveValidationService = asyncDriveValidationService;
        this.realtimeEventDispatcher = realtimeEventDispatcher;
    }

    @Transactional(readOnly = true)
    public CaretakerApplicationStatusResponse getMyApplicationStatus(Long userId) {
        User user = requireCaretaker(userId);
        boolean approved = profileRepository.existsByUserId(user.getId());
        List<CaretakerApplication> applications = applicationRepository
                .findByUserIdOrderByApplicationNumberDesc(user.getId());

        boolean hasPending = applications.stream()
                .anyMatch(app -> app.getStatus() == VerificationStatus.PENDING);
        boolean canSubmitNew = !approved && !hasPending;
        String latestStatus = applications.isEmpty() ? "NONE" : applications.getFirst().getStatus().name();
        int nextApplicationNumber = applications.isEmpty() ? 1 : applications.getFirst().getApplicationNumber() + 1;

        List<CaretakerApplicationResponse> responseList = applications.stream()
                .map(CaretakerApplicationResponse::from)
                .toList();

        return new CaretakerApplicationStatusResponse(
                approved,
                canSubmitNew,
                latestStatus,
                nextApplicationNumber,
                responseList
        );
    }

    @Transactional
    public CaretakerApplicationResponse submitApplication(Long userId, CaretakerApplicationRequest request) {
        User caretaker = requireCaretaker(userId);
        validateApplicantAge(request.dob());

        if (profileRepository.existsByUserId(caretaker.getId())
                || applicationRepository.existsByUserIdAndStatus(caretaker.getId(), VerificationStatus.APPROVED)) {
            throw new ConflictException("Your caretaker account is already approved.");
        }

        if (applicationRepository.existsByUserIdAndStatus(caretaker.getId(), VerificationStatus.PENDING)) {
            throw new ConflictException(
                    "You already have a pending application under Admin review. Please wait for the decision."
            );
        }

        String normalizedDriveLink = GoogleDriveUrlValidator.validateAndNormalize(request.documentsDriveLink());
        int nextApplicationNumber = applicationRepository.findMaxApplicationNumberByUserId(caretaker.getId()) + 1;

        CaretakerApplication application = new CaretakerApplication(
                caretaker,
                nextApplicationNumber,
                request.dob(),
                request.qualification().trim(),
                normalizeOptionalText(request.experience()),
                normalizedDriveLink
        );

        CaretakerApplication saved;
        try {
            saved = applicationRepository.saveAndFlush(application);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("An application submission is already in progress. Please refresh.");
        }

        Long applicationId = saved.getId();
        boolean isReapplication = saved.getApplicationNumber() > 1;
        AdminQueueAlertEvent adminAlert = AdminQueueAlertEvent.of(
                isReapplication ? "REAPPLICATION" : "NEW_APPLICATION",
                applicationId,
                isReapplication
                        ? "Caretaker Re-Application #" + saved.getApplicationNumber()
                        : "New Caretaker Application #1",
                caretaker.getFullName() + " submitted verification documents (" + saved.getQualification() + ").",
                caretaker.getId(),
                caretaker.getFullName()
        );
        TransactionCommitHooks.runAfterCommit(() -> {
            asyncDriveValidationService.validateApplicationDriveLink(applicationId);
            realtimeEventDispatcher.publishAdminAlert(adminAlert);
        });

        return CaretakerApplicationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CertificationResponse> getMyCertifications(Long userId) {
        CaretakerProfile profile = requireApprovedProfile(userId);
        return certificationRepository.findByCaretakerProfileIdOrderBySubmittedAtDesc(profile.getId())
                .stream()
                .map(CertificationResponse::from)
                .toList();
    }

    @Transactional
    public CertificationResponse submitCertification(Long userId, NewCertificationRequest request) {
        CaretakerProfile profile = requireApprovedProfile(userId);
        String normalizedDriveLink = GoogleDriveUrlValidator.validateAndNormalize(request.documentDriveLink());

        CaretakerCertification certification = new CaretakerCertification(
                profile,
                request.title().trim(),
                request.issuingOrganization().trim(),
                normalizedDriveLink
        );

        CaretakerCertification saved = certificationRepository.saveAndFlush(certification);
        Long certificationId = saved.getId();
        User caretaker = profile.getUser();
        AdminQueueAlertEvent adminAlert = AdminQueueAlertEvent.of(
                "NEW_CERTIFICATION",
                certificationId,
                "New Certification Verification Request",
                caretaker.getFullName() + " submitted \"" + saved.getTitle() + "\" for verification.",
                caretaker.getId(),
                caretaker.getFullName()
        );
        TransactionCommitHooks.runAfterCommit(() -> {
            asyncDriveValidationService.validateCertificationDriveLink(certificationId);
            realtimeEventDispatcher.publishAdminAlert(adminAlert);
        });

        return CertificationResponse.from(saved);
    }

    private User requireCaretaker(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User account not found."));
        if (user.getRole() != Role.CARETAKER) {
            throw new ForbiddenException("Only caretaker accounts can perform this action.");
        }
        return user;
    }

    private CaretakerProfile requireApprovedProfile(Long userId) {
        requireCaretaker(userId);
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ForbiddenException(
                        "Your caretaker application must be approved by an Admin before adding new certifications."
                ));
    }

    private void validateApplicantAge(LocalDate dob) {
        LocalDate today = LocalDate.now(DHAKA_ZONE);
        if (dob.isAfter(today.minusYears(MIN_APPLICANT_AGE))) {
            throw new BadRequestException("Caretaker applicants must be at least 18 years old.");
        }
        if (dob.isBefore(today.minusYears(MAX_APPLICANT_AGE))) {
            throw new BadRequestException("Please enter a valid date of birth.");
        }
    }

    private String normalizeOptionalText(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
