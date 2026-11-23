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
import com.carebridge.user.Role;
import com.carebridge.user.User;
import com.carebridge.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    public CaretakerVerificationService(
            UserRepository userRepository,
            CaretakerApplicationRepository applicationRepository,
            CaretakerProfileRepository profileRepository,
            CaretakerCertificationRepository certificationRepository,
            AsyncDriveValidationService asyncDriveValidationService
    ) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.profileRepository = profileRepository;
        this.certificationRepository = certificationRepository;
        this.asyncDriveValidationService = asyncDriveValidationService;
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
        runAfterCommit(() -> asyncDriveValidationService.validateApplicationDriveLink(applicationId));

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
        runAfterCommit(() -> asyncDriveValidationService.validateCertificationDriveLink(certificationId));

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

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
