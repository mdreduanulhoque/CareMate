package com.carebridge.feedback;

import com.carebridge.booking.ContractLifecycleScheduler;
import com.carebridge.booking.ContractStatus;
import com.carebridge.booking.HireContract;
import com.carebridge.booking.HireContractRepository;
import com.carebridge.caretaker.CaretakerProfile;
import com.carebridge.caretaker.CaretakerProfileRepository;
import com.carebridge.caretaker.drive.DriveLinkProbeClient;
import com.carebridge.caretaker.drive.DriveProbeResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedbackAndWorkersIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ContractLifecycleScheduler contractLifecycleScheduler;

    @Autowired
    private ReviewLockMaintenanceScheduler reviewLockMaintenanceScheduler;

    @Autowired
    private HireContractRepository hireContractRepository;

    @Autowired
    private CaretakerProfileRepository caretakerProfileRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @MockitoBean
    private DriveLinkProbeClient driveLinkProbeClient;

    @BeforeEach
    void setupDriveStub() {
        when(driveLinkProbeClient.probe(anyString()))
                .thenReturn(new DriveProbeResult(true, "Publicly reachable (HTTP 200)."));
    }

    @Test
    void backgroundLifecycleWorkersAndBidirectionalSevenDayFeedbackWorkEndToEnd() throws Exception {
        String adminToken = loginAsSeededAdmin();
        AuthInfo customer = registerCustomer("Nabila Chowdhury", "01371000001", "Dhanmondi, Dhaka");
        AuthInfo intruderCustomer = registerCustomer("Outsider Family", "01371000002", "Mirpur, Dhaka");
        AuthInfo unverifiedCaretaker = registerCaretaker("Pending Nurse", "01371000003", "Banani, Dhaka");
        AuthInfo nurse = registerCaretaker("Nurse Taslima", "01371000004", "Dhanmondi, Dhaka");
        approveCaretaker(nurse.token(), adminToken, "BSc in Nursing");

        // 1. Create & accept a future SCHEDULED contract [now + 2h, now + 6h]
        Instant now = Instant.now();
        Instant start = now.plus(2, ChronoUnit.HOURS);
        Instant end = now.plus(6, ChronoUnit.HOURS);

        long contractId = createAndAcceptHire(customer.token(), nurse.token(), nurse.userId(), start, end);
        assertThat(hireContractRepository.findById(contractId).orElseThrow().getStatus())
                .isEqualTo(ContractStatus.SCHEDULED);

        // 2. Background worker promotes SCHEDULED -> ACTIVE once start time arrives
        ContractLifecycleScheduler.SweepResult activationSweep =
                contractLifecycleScheduler.runLifecycleSweep(start.plus(30, ChronoUnit.MINUTES));
        assertThat(activationSweep.activationDispatched()).isGreaterThanOrEqualTo(1);
        activationSweep.awaitAll();

        assertThat(hireContractRepository.findById(contractId).orElseThrow().getStatus())
                .isEqualTo(ContractStatus.ACTIVE);

        // 3. Background worker auto-completes ACTIVE -> COMPLETED_PENDING_SETTLEMENT once end time passes
        ContractLifecycleScheduler.SweepResult completionSweep =
                contractLifecycleScheduler.runLifecycleSweep(end.plus(5, ChronoUnit.MINUTES));
        assertThat(completionSweep.completionDispatched()).isGreaterThanOrEqualTo(1);
        completionSweep.awaitAll();

        HireContract completed = hireContractRepository.findById(contractId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(ContractStatus.COMPLETED_PENDING_SETTLEMENT);
        assertThat(completed.getActualEndTime()).isNotNull();

        // Idempotency check: running completion sweep a second time creates zero duplicates
        contractLifecycleScheduler.runLifecycleSweep(end.plus(10, ChronoUnit.MINUTES)).awaitAll();
        assertThat(reviewRepository.findAllByContractId(contractId)).hasSize(2);

        // 4. Both Customer and Caretaker see their pending review forms
        MvcResult customerPendingRes = mockMvc.perform(get("/api/reviews/pending")
                        .header("Authorization", "Bearer " + customer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].contractId").value(contractId))
                .andExpect(jsonPath("$[0].submitted").value(false))
                .andReturn();
        long customerReviewId = objectMapper.readTree(customerPendingRes.getResponse().getContentAsString())
                .get(0).get("id").asLong();

        MvcResult caretakerPendingRes = mockMvc.perform(get("/api/reviews/pending")
                        .header("Authorization", "Bearer " + nurse.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].contractId").value(contractId))
                .andReturn();
        long caretakerReviewId = objectMapper.readTree(caretakerPendingRes.getResponse().getContentAsString())
                .get(0).get("id").asLong();

        // RBAC & IDOR checks
        mockMvc.perform(post("/api/reviews/" + customerReviewId + "/submit")
                        .header("Authorization", "Bearer " + nurse.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "rating": 5, "comment": "Spoof", "wouldEngageAgain": true }
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reviews/contract/" + contractId)
                        .header("Authorization", "Bearer " + intruderCustomer.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reviews/customer/" + customer.userId())
                        .header("Authorization", "Bearer " + customer.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reviews/caretaker/" + nurse.userId())
                        .header("Authorization", "Bearer " + unverifiedCaretaker.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reviews/admin")
                        .header("Authorization", "Bearer " + customer.token()))
                .andExpect(status().isForbidden());

        // 5. Customer submits initial 4-star review (wouldEngageAgain=false)
        MvcResult firstSubmitRes = mockMvc.perform(post("/api/reviews/" + customerReviewId + "/submit")
                        .header("Authorization", "Bearer " + customer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 4,
                                  "comment": "Punctual and polite during hospital shift.",
                                  "wouldEngageAgain": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submitted").value(true))
                .andExpect(jsonPath("$.edited").value(false))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.editableUntil").isNotEmpty())
                .andReturn();
        String originalEditableUntil = objectMapper.readTree(firstSubmitRes.getResponse().getContentAsString())
                .get("editableUntil").asText();

        CaretakerProfile afterInitial = caretakerProfileRepository.findByUserId(nurse.userId()).orElseThrow();
        assertThat(afterInitial.getAvgRating()).isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(afterInitial.getHireAgainPercentage()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(afterInitial.getCompletedHiresCount()).isGreaterThanOrEqualTo(1);

        // 6. Customer edits review within 7 days -> sets edited=true, preserves editableUntil, recalculates metrics
        mockMvc.perform(put("/api/reviews/" + customerReviewId)
                        .header("Authorization", "Bearer " + customer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 5,
                                  "comment": "Actually handled post-op medications brilliantly!",
                                  "wouldEngageAgain": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.wouldEngageAgain").value(true))
                .andExpect(jsonPath("$.edited").value(true))
                .andExpect(jsonPath("$.editableUntil").value(originalEditableUntil));

        // Caretaker public reviews & CaretakerProfile reflect updated 5.00 rating & 100% hire again
        mockMvc.perform(get("/api/reviews/caretaker/" + nurse.userId())
                        .header("Authorization", "Bearer " + intruderCustomer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReviews").value(1))
                .andExpect(jsonPath("$.avgRating").value(5.0))
                .andExpect(jsonPath("$.wouldEngageAgainPercentage").value(100.0))
                .andExpect(jsonPath("$.reviews[0].edited").value(true));

        // 7. Caretaker submits review of Customer -> visible on /api/reviews/customer/{id} & incoming HireContractResponse
        mockMvc.perform(post("/api/reviews/" + caretakerReviewId + "/submit")
                        .header("Authorization", "Bearer " + nurse.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 5,
                                  "comment": "Respectful family, paid on time and gave clear instructions.",
                                  "wouldEngageAgain": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submitted").value(true));

        mockMvc.perform(get("/api/reviews/customer/" + customer.userId())
                        .header("Authorization", "Bearer " + nurse.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReviews").value(1))
                .andExpect(jsonPath("$.avgRating").value(5.0))
                .andExpect(jsonPath("$.wouldEngageAgainPercentage").value(100.0));

        // New hire request from this customer now carries live customer trust metrics for caretaker evaluation
        Instant nextStart = Instant.now().plus(24, ChronoUnit.HOURS);
        Instant nextEnd = nextStart.plus(4, ChronoUnit.HOURS);
        mockMvc.perform(post("/api/hires")
                        .header("Authorization", "Bearer " + customer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caretakerUserId": %d,
                                  "agreedRate": 1800.00,
                                  "rateType": "FIXED",
                                  "scheduledStartTime": "%s",
                                  "scheduledEndTime": "%s"
                                }
                                """.formatted(nurse.userId(), nextStart.toString(), nextEnd.toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerReviewCount").value(1))
                .andExpect(jsonPath("$.customerAvgRating").value(5.0))
                .andExpect(jsonPath("$.customerWorkAgainPercentage").value(100.0));

        // 8. Admin can inspect all submitted reviews
        mockMvc.perform(get("/api/reviews/admin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        // 9. Scheduled 7-day lock-window maintenance locks reviews after 7 days & blocks any further edits (409)
        int lockedCount = reviewLockMaintenanceScheduler.runReviewLockMaintenance(
                Instant.now().plus(8, ChronoUnit.DAYS)
        );
        assertThat(lockedCount).isGreaterThanOrEqualTo(2);

        mockMvc.perform(put("/api/reviews/" + customerReviewId)
                        .header("Authorization", "Bearer " + customer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 1,
                                  "comment": "Attempted edit after 7 days",
                                  "wouldEngageAgain": false
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void endingServiceEarlyAutomaticallyGeneratesBothPendingFeedbackForms() throws Exception {
        String adminToken = loginAsSeededAdmin();
        AuthInfo customer = registerCustomer("EarlyEnd Customer", "01371000011", "Uttara, Dhaka");
        AuthInfo nurse = registerCaretaker("EarlyEnd Nurse", "01371000012", "Uttara, Dhaka");
        approveCaretaker(nurse.token(), adminToken, "Diploma in Nursing");

        Instant activeStart = Instant.now().minus(2, ChronoUnit.MINUTES);
        Instant activeEnd = Instant.now().plus(5, ChronoUnit.HOURS);

        long contractId = createAndAcceptHire(customer.token(), nurse.token(), nurse.userId(), activeStart, activeEnd);

        mockMvc.perform(post("/api/hires/" + contractId + "/end-early")
                        .header("Authorization", "Bearer " + customer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Patient discharged early by doctor." }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED_PENDING_SETTLEMENT"))
                .andExpect(jsonPath("$.myReviewId").isNotEmpty())
                .andExpect(jsonPath("$.myReviewSubmitted").value(false));

        assertThat(reviewRepository.findAllByContractId(contractId)).hasSize(2);
    }

    private long createAndAcceptHire(
            String customerToken,
            String caretakerToken,
            long caretakerUserId,
            Instant start,
            Instant end
    ) throws Exception {
        MvcResult createRes = mockMvc.perform(post("/api/hires")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caretakerUserId": %d,
                                  "agreedRate": 2000.00,
                                  "rateType": "DAILY",
                                  "scheduledStartTime": "%s",
                                  "scheduledEndTime": "%s",
                                  "notes": "Hospital bedside shift"
                                }
                                """.formatted(caretakerUserId, start.toString(), end.toString())))
                .andExpect(status().isCreated())
                .andReturn();
        long contractId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/hires/" + contractId + "/accept")
                        .header("Authorization", "Bearer " + caretakerToken))
                .andExpect(status().isOk());
        return contractId;
    }

    private String loginAsSeededAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobileNumber": "01700000000",
                                  "password": "AdminTestPass123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private AuthInfo registerCustomer(String name, String mobile, String address) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "%s",
                                  "mobileNumber": "%s",
                                  "address": "%s",
                                  "password": "CustomerPassword123!"
                                }
                                """.formatted(name, mobile, address)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthInfo(root.get("accessToken").asText(), root.get("user").get("id").asLong());
    }

    private AuthInfo registerCaretaker(String name, String mobile, String address) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register/caretaker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "%s",
                                  "mobileNumber": "%s",
                                  "address": "%s",
                                  "password": "CaretakerPassword123!"
                                }
                                """.formatted(name, mobile, address)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthInfo(root.get("accessToken").asText(), root.get("user").get("id").asLong());
    }

    private void approveCaretaker(String caretakerToken, String adminToken, String qualification) throws Exception {
        MvcResult appRes = mockMvc.perform(post("/api/caretaker/applications")
                        .header("Authorization", "Bearer " + caretakerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dob": "1992-07-10",
                                  "qualification": "%s",
                                  "experience": "4 years hospital ward care",
                                  "documentsDriveLink": "https://drive.google.com/file/d/public-phase6-doc/view"
                                }
                                """.formatted(qualification)))
                .andExpect(status().isCreated())
                .andReturn();
        long appId = objectMapper.readTree(appRes.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/admin/applications/" + appId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    private record AuthInfo(String token, long userId) {
    }
}
