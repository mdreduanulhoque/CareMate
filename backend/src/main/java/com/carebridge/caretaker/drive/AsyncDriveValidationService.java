package com.carebridge.caretaker.drive;

import com.carebridge.caretaker.CaretakerApplication;
import com.carebridge.caretaker.CaretakerApplicationRepository;
import com.carebridge.caretaker.CaretakerCertification;
import com.carebridge.caretaker.CaretakerCertificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executes Google Drive reachability checks asynchronously on the dedicated bounded pool.
 * Performs network I/O outside any open database transaction to avoid holding DB connections.
 */
@Service
public class AsyncDriveValidationService {

    private static final Logger log = LoggerFactory.getLogger(AsyncDriveValidationService.class);

    private final DriveLinkProbeClient probeClient;
    private final CaretakerApplicationRepository applicationRepository;
    private final CaretakerCertificationRepository certificationRepository;
    private final TransactionTemplate transactionTemplate;

    public AsyncDriveValidationService(
            DriveLinkProbeClient probeClient,
            CaretakerApplicationRepository applicationRepository,
            CaretakerCertificationRepository certificationRepository,
            TransactionTemplate transactionTemplate
    ) {
        this.probeClient = probeClient;
        this.applicationRepository = applicationRepository;
        this.certificationRepository = certificationRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Async("driveValidationExecutor")
    public void validateApplicationDriveLink(Long applicationId) {
        String driveUrl = transactionTemplate.execute(status ->
                applicationRepository.findById(applicationId)
                        .map(CaretakerApplication::getDocumentsDriveLink)
                        .orElse(null)
        );
        if (driveUrl == null) {
            return;
        }

        DriveProbeResult result = probeClient.probe(driveUrl);
        transactionTemplate.executeWithoutResult(status ->
                applicationRepository.findById(applicationId).ifPresent(app -> {
                    app.markDriveCheckResult(result.reachable(), result.note());
                    applicationRepository.save(app);
                })
        );
        log.debug("Application {} Drive link check completed: reachable={}", applicationId, result.reachable());
    }

    @Async("driveValidationExecutor")
    public void validateCertificationDriveLink(Long certificationId) {
        String driveUrl = transactionTemplate.execute(status ->
                certificationRepository.findById(certificationId)
                        .map(CaretakerCertification::getDocumentDriveLink)
                        .orElse(null)
        );
        if (driveUrl == null) {
            return;
        }

        DriveProbeResult result = probeClient.probe(driveUrl);
        transactionTemplate.executeWithoutResult(status ->
                certificationRepository.findById(certificationId).ifPresent(cert -> {
                    cert.markDriveCheckResult(result.reachable(), result.note());
                    certificationRepository.save(cert);
                })
        );
        log.debug("Certification {} Drive link check completed: reachable={}", certificationId, result.reachable());
    }
}
