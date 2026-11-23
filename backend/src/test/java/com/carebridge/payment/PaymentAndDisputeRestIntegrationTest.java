package com.carebridge.payment;

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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentAndDisputeRestIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DriveLinkProbeClient driveLinkProbeClient;

    @BeforeEach
    void setupDriveStub() {
        when(driveLinkProbeClient.probe(anyString()))
                .thenReturn(new DriveProbeResult(true, "Publicly reachable (HTTP 200)."));
    }

    @Test
    void dualPaymentConfirmDisputeResolutionAndAdminUsersWorkEndToEnd() throws Exception {
        String adminToken = loginAsSeededAdmin();
        AuthInfo customer = registerCustomer("Faruk Hossain", "01481000001", "Dhanmondi, Dhaka");
        AuthInfo intruder = registerCustomer("Outsider Person", "01481000002", "Mirpur, Dhaka");
        AuthInfo nurse = registerCaretaker("Nurse Halima", "01481000003", "Dhanmondi, Dhaka");
        approveCaretaker(nurse.token(), adminToken, "BSc in Nursing");

        // --- PATH A: Clean Dual Payment Confirmation -> CLOSED_PAID ---
        long paidContractId = createActiveAndEndEarlyContract(customer.token(), nurse.token(), nurse.userId());

        // Caretaker cannot confirm before Customer marks Payment Done (409)
        mockMvc.perform(post("/api/payments/contracts/" + paidContractId + "/caretaker-confirm")
                        .header("Authorization", "Bearer " + nurse.token()))
                .andExpect(status().isConflict());

        // Intruder cannot mark Customer's payment done (403)
        mockMvc.perform(post("/api/payments/contracts/" + paidContractId + "/customer-done")
                        .header("Authorization", "Bearer " + intruder.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "paymentReferenceNote": "Fake Trx" }
                                """))
                .andExpect(status().isForbidden());

        // Customer marks Payment Done
        mockMvc.perform(post("/api/payments/contracts/" + paidContractId + "/customer-done")
                        .header("Authorization", "Bearer " + customer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "paymentReferenceNote": "bKash TrxID BK901234 — 2000 BDT" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerPaymentConfirmed").value(true))
                .andExpect(jsonPath("$.paymentReferenceNote").value("bKash TrxID BK901234 — 2000 BDT"))
                .andExpect(jsonPath("$.status").value("COMPLETED_PENDING_SETTLEMENT"));

        // Duplicate Customer mark-done rejected (409)
        mockMvc.perform(post("/api/payments/contracts/" + paidContractId + "/customer-done")
                        .header("Authorization", "Bearer " + customer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        // Caretaker confirms Payment Received -> CLOSED_PAID
        mockMvc.perform(post("/api/payments/contracts/" + paidContractId + "/caretaker-confirm")
                        .header("Authorization", "Bearer " + nurse.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED_PAID"))
                .andExpect(jsonPath("$.caretakerPaymentConfirmed").value(true));

        // --- PATH B: Caretaker Refuses -> PAYMENT_DISPUTED -> 3-Way Room -> DISPUTE_RESOLVED ---
        long disputedContractId = createActiveAndEndEarlyContract(customer.token(), nurse.token(), nurse.userId());

        mockMvc.perform(post("/api/payments/contracts/" + disputedContractId + "/customer-done")
                        .header("Authorization", "Bearer " + customer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "paymentReferenceNote": "Nagad TrxID NG774122 — 2000 BDT" }
                                """))
                .andExpect(status().isOk());

        // Caretaker refuses payment -> PAYMENT_DISPUTED + PaymentDispute OPEN
        mockMvc.perform(post("/api/payments/contracts/" + disputedContractId + "/caretaker-refuse")
                        .header("Authorization", "Bearer " + nurse.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "caretakerStatement": "No Nagad transfer arrived in my account after 3 hours." }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractId").value(disputedContractId))
                .andExpect(jsonPath("$.contractStatus").value("PAYMENT_DISPUTED"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.canSendMessages").value(true));

        // Customer updates formal statement; outsider blocked (403)
        mockMvc.perform(put("/api/disputes/contract/" + disputedContractId + "/statement")
                        .header("Authorization", "Bearer " + customer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "statement": "Sent 2000 BDT via Nagad at 3:10 PM, TrxID NG774122 to 01481000003." }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerStatement").value(
                        "Sent 2000 BDT via Nagad at 3:10 PM, TrxID NG774122 to 01481000003."
                ));

        mockMvc.perform(get("/api/disputes/contract/" + disputedContractId)
                        .header("Authorization", "Bearer " + intruder.token()))
                .andExpect(status().isForbidden());

        // Admin joins dispute -> UNDER_REVIEW
        mockMvc.perform(post("/api/disputes/contract/" + disputedContractId + "/join")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.assignedAdminName").isNotEmpty());

        // 3-Way Dispute Room REST messages (Customer, Caretaker, Admin); outsider denied (403)
        mockMvc.perform(post("/api/chats/dispute/" + disputedContractId + "/messages")
                        .header("Authorization", "Bearer " + customer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "Here is my Nagad transaction reference NG774122." }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomType").value("DISPUTE"))
                .andExpect(jsonPath("$.senderRole").value("CUSTOMER"));

        mockMvc.perform(post("/api/chats/dispute/" + disputedContractId + "/messages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "Admin reviewing statement from both parties." }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderRole").value("ADMIN"));

        mockMvc.perform(get("/api/chats/dispute/" + disputedContractId)
                        .header("Authorization", "Bearer " + intruder.token()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/chats/dispute/" + disputedContractId)
                        .header("Authorization", "Bearer " + nurse.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2));

        // Non-admin cannot resolve dispute (403)
        mockMvc.perform(post("/api/disputes/contract/" + disputedContractId + "/resolve")
                        .header("Authorization", "Bearer " + customer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "adminResolutionNote": "Trying to self-resolve" }
                                """))
                .andExpect(status().isForbidden());

        // Admin resolves dispute -> DISPUTE_RESOLVED + room becomes read-only
        mockMvc.perform(post("/api/disputes/contract/" + disputedContractId + "/resolve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "adminResolutionNote": "Verified Nagad transfer reversed and resent; caretaker confirmed settlement." }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.contractStatus").value("DISPUTE_RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
                .andExpect(jsonPath("$.canSendMessages").value(false));

        // After resolution: history stays readable, but sending new dispute messages is blocked (403)
        mockMvc.perform(get("/api/chats/dispute/" + disputedContractId)
                        .header("Authorization", "Bearer " + customer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispute.canSendMessages").value(false));

        mockMvc.perform(post("/api/chats/dispute/" + disputedContractId + "/messages")
                        .header("Authorization", "Bearer " + customer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "Message after resolution" }
                                """))
                .andExpect(status().isForbidden());

        // --- PATH C: Admin Users Management ---
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + customer.token()))
                .andExpect(status().isForbidden());

        MvcResult usersRes = mockMvc.perform(get("/api/admin/users")
                        .param("keyword", "01481000001")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mobileNumber").value("01481000001"))
                .andReturn();
        assertThat(usersRes.getResponse().getContentAsString()).doesNotContain("passwordHash");

        // Admin can deactivate & reactivate a non-admin user, and moderate caretaker availability
        mockMvc.perform(patch("/api/admin/users/" + intruder.userId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "active": false }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(patch("/api/admin/users/caretakers/" + nurse.userId() + "/availability")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "available": false }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caretakerAvailable").value(false));
    }

    private long createActiveAndEndEarlyContract(
            String customerToken,
            String caretakerToken,
            long caretakerUserId
    ) throws Exception {
        Instant start = Instant.now().minus(2, ChronoUnit.MINUTES);
        Instant end = Instant.now().plus(4, ChronoUnit.HOURS);

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
                                  "notes": "Hospital care shift"
                                }
                                """.formatted(caretakerUserId, start.toString(), end.toString())))
                .andExpect(status().isCreated())
                .andReturn();
        long contractId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/hires/" + contractId + "/accept")
                        .header("Authorization", "Bearer " + caretakerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/hires/" + contractId + "/end-early")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Shift completed early for settlement test." }
                                """))
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
                                  "dob": "1992-09-14",
                                  "qualification": "%s",
                                  "experience": "5 years clinical bedside experience",
                                  "documentsDriveLink": "https://drive.google.com/file/d/public-phase7-doc/view"
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
