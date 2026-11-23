package com.carebridge.caretaker;

import com.carebridge.caretaker.drive.AsyncDriveValidationService;
import com.carebridge.caretaker.dto.AdminApplicationResponse;
import com.carebridge.caretaker.dto.AdminCertificationResponse;
import com.carebridge.common.BadRequestException;
import com.carebridge.common.ConflictException;
import com.carebridge.common.NotFoundException;
import com.carebridge.user.User;
import com.carebridge.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
public class AdminVerificationService {

    private static final int MAX_LOCATION_AREA_LENGTH = 160;

    private final CaretakerApplicationRepository applicationRepository;
    private final CaretakerProfileRepository profileRepository;
    private final CaretakerCertificationRepository certificationRepository;
    private final UserRepository userRepository;
    private final AsyncDriveValidationService asyncDriveValidationService;

    public AdminVerificationService(
            CaretakerApplicationRepository applicationRepository,
            CaretakerProfileRepository profileRepository,
            CaretakerCertificationRepository certificationRepository,
            UserRepository userRepository,
            AsyncDriveValidationService asyncDriveValidationService
    ) {
        this.applicationRepository = applicationRepository;
        this.profileRepository = profileRepository;
        this.certificationRepository = certificationRepository;
        this.userRepository = userRepository;
        this.asyncDriveValidationService = asyncDriveValidationService;
    }

    @Transactional(readOnly = true)
    public List<AdminApplicationResponse> listApplications(VerificationStatus statusFilter) {
        return applicationRepository.findAllWithUsersByStatus(statusFilter)
                .stream()
                .map(AdminApplicationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminApplicationResponse> getApplicantHistory(Long applicantUserId) {
        if (!userRepository.existsById(applicantUserId)) {
            throw new NotFoundException("Applicant user not found.");
        }
        return applicationRepository.findUserHistoryWithAdmin(applicantUserId)
                .stream()
                .map(AdminApplicationResponse::from)
                .toList();
    }

    @Transactional
    public AdminApplicationResponse approveApplication(Long applicationId, Long adminUserId) {
        CaretakerApplication application = applicationRepository.findByIdWithUsers(applicationId)
                .orElseThrow(() -> new NotFoundException("Caretaker application not found."));

        if (application.getStatus() != VerificationStatus.PENDING) {
            throw new ConflictException("Only pending applications can be approved.");
        }

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new NotFoundException("Admin account not found."));

        application.approve(admin);
        CaretakerApplication saved = applicationRepository.save(application);

        User applicant = saved.getUser();
        if (!profileRepository.existsByUserId(applicant.getId())) {
            String initialArea = truncateArea(applicant.getAddress());
            CaretakerProfile profile = new CaretakerProfile(applicant, saved.getQualification(), initialArea);
            profileRepository.save(profile);
        }

        return AdminApplicationResponse.from(saved);
    }

    @Transactional
    public AdminApplicationResponse rejectApplication(Long applicationId, Long adminUserId, String rawReason) {
        String reason = normalizeMandatoryReason(rawReason);

        CaretakerApplication application = applicationRepository.findByIdWithUsers(applicationId)
                .orElseThrow(() -> new NotFoundException("Caretaker application not found."));

        if (application.getStatus() != VerificationStatus.PENDING) {
            throw new ConflictException("Only pending applications can be rejected.");
        }

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new NotFoundException("Admin account not found."));

        application.reject(admin, reason);
        CaretakerApplication saved = applicationRepository.save(application);
        return AdminApplicationResponse.from(saved);
    }

    @Transactional
    public AdminApplicationResponse recheckApplicationDriveLink(Long applicationId) {
        CaretakerApplication application = applicationRepository.findByIdWithUsers(applicationId)
                .orElseThrow(() -> new NotFoundException("Caretaker application not found."));
        application.resetDriveCheckPending();
        CaretakerApplication saved = applicationRepository.save(application);
        runAfterCommit(() -> asyncDriveValidationService.validateApplicationDriveLink(saved.getId()));
        return AdminApplicationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AdminCertificationResponse> listCertifications(VerificationStatus statusFilter) {
        return certificationRepository.findAllWithCaretakerByStatus(statusFilter)
                .stream()
                .map(AdminCertificationResponse::from)
                .toList();
    }

    @Transactional
    public AdminCertificationResponse approveCertification(Long certificationId, Long adminUserId) {
        CaretakerCertification certification = certificationRepository.findByIdWithCaretaker(certificationId)
                .orElseThrow(() -> new NotFoundException("Certification request not found."));

        if (certification.getVerificationStatus() != VerificationStatus.PENDING) {
            throw new ConflictException("Only pending certifications can be approved.");
        }

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new NotFoundException("Admin account not found."));

        certification.approve(admin);
        CaretakerCertification saved = certificationRepository.save(certification);
        return AdminCertificationResponse.from(saved);
    }

    @Transactional
    public AdminCertificationResponse rejectCertification(
            Long certificationId,
            Long adminUserId,
            String rawReason
    ) {
        String reason = normalizeMandatoryReason(rawReason);

        CaretakerCertification certification = certificationRepository.findByIdWithCaretaker(certificationId)
                .orElseThrow(() -> new NotFoundException("Certification request not found."));

        if (certification.getVerificationStatus() != VerificationStatus.PENDING) {
            throw new ConflictException("Only pending certifications can be rejected.");
        }

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new NotFoundException("Admin account not found."));

        certification.reject(admin, reason);
        CaretakerCertification saved = certificationRepository.save(certification);
        return AdminCertificationResponse.from(saved);
    }

    @Transactional
    public AdminCertificationResponse recheckCertificationDriveLink(Long certificationId) {
        CaretakerCertification certification = certificationRepository.findByIdWithCaretaker(certificationId)
                .orElseThrow(() -> new NotFoundException("Certification request not found."));
        certification.resetDriveCheckPending();
        CaretakerCertification saved = certificationRepository.save(certification);
        runAfterCommit(() -> asyncDriveValidationService.validateCertificationDriveLink(saved.getId()));
        return AdminCertificationResponse.from(saved);
    }

    private String normalizeMandatoryReason(String rawReason) {
        if (rawReason == null || rawReason.trim().length() < 5) {
            throw new BadRequestException("A clear rejection reason (at least 5 characters) is mandatory.");
        }
        return rawReason.trim();
    }

    private String truncateArea(String address) {
        String trimmed = address == null ? "Dhaka" : address.trim();
        if (trimmed.length() <= MAX_LOCATION_AREA_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_LOCATION_AREA_LENGTH);
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
