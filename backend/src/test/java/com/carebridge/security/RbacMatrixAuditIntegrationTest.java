package com.carebridge.security;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exhaustive automated verification of CareMate.md Section 2.4 Role Permissions Matrix
 * across all four personas (Customer, Unverified Caretaker, Approved Caretaker, Admin),
 * unauthenticated 401 enforcement, IDOR isolation, phone privacy on the feed, and
 * HTTP security response headers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RbacMatrixAuditIntegrationTest {

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
    void unauthenticatedRequestsReceive401AndSecurityHeadersArePresent() throws Exception {
        // Public health check includes hardened HTTP security headers
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));

        // Protected endpoints must reject anonymous callers with 401 Unauthorized
        String[] protectedGetPaths = {
                "/api/auth/me",
                "/api/feed/caretakers",
                "/api/caretaker/applications/me",
                "/api/caretaker/profile/me",
                "/api/caretaker/certifications/me",
                "/api/chats/rooms",
                "/api/hires",
                "/api/reviews/pending",
                "/api/disputes",
                "/api/admin/applications",
                "/api/admin/certifications",
                "/api/admin/users"
        };

        for (String path : protectedGetPaths) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"));
        }

        // Tampered / malformed bearer token must also return 401
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void section24RolePermissionsMatrixEnforcedAcrossAllFourPersonas() throws Exception {
        String adminToken = loginAsSeededAdmin();
        AuthInfo customer = registerCustomer("Audit Relative", "01399000001", "Dhanmondi, Dhaka");
        AuthInfo outsiderCustomer = registerCustomer("Outsider Relative", "01399000002", "Mirpur, Dhaka");
        AuthInfo unverifiedCaretaker = registerCaretaker("Unverified Nurse", "01399000003", "Uttara, Dhaka");
        AuthInfo approvedCaretaker = registerCaretaker("Approved Nurse", "01399000004", "Gulshan, Dhaka");
        AuthInfo secondApprovedCaretaker = registerCaretaker("Peer Nurse", "01399000005", "Banani, Dhaka");

        approveCaretaker(approvedCaretaker.token(), adminToken, "BSc in Nursing");
        approveCaretaker(secondApprovedCaretaker.token(), adminToken, "Diploma in Nursing");

        // ------------------------------------------------------------------
        // 1. Browse and Filter Hunters Feed: Customer=Yes, Unverified=No, Approved=Read-Only, Admin=Yes
        // ------------------------------------------------------------------
        MvcResult feedResult = mockMvc.perform(get("/api/feed/caretakers")
                        .header("Authorization", "Bearer " + customer.token()))
                .andExpect(status().isOk())
                .andReturn();
        // Verify phone privacy on public feed list (caretaker mobile must NEVER leak on feed cards)
        assertThat(feedResult.getResponse().getContentAsString()).doesNotContain("01399000004");

        MvcResult detailBeforeUnlock = mockMvc.perform(get("/api/feed/caretakers/" + approvedCaretaker.userId())
                        .header("Authorization", "Bearer " + customer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactUnlocked").value(false))
                .andReturn();
        assertThat(detailBeforeUnlock.getResponse().getContentAsString()).doesNotContain("01399000004");

        mockMvc.perform(get("/api/feed/caretakers")
                        .header("Authorization", "Bearer " + unverifiedCaretaker.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/feed/caretakers/" + approvedCaretaker.userId())
                        .header("Authorization", "Bearer " + unverifiedCaretaker.token()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/feed/caretakers")
                        .header("Authorization", "Bearer " + approvedCaretaker.token()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/feed/caretakers")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // ------------------------------------------------------------------
        // 2. Submit Initial Application: Customer=No, Unverified=Yes, Approved=No(409), Admin=No
        // ------------------------------------------------------------------
        String applicationPayload = """
                {
                  "dob": "1994-06-15",
                  "qualification": "Diploma in Nursing",
                  "experience": "4 years hospital care",
                  "documentsDriveLink": "https://drive.google.com/file/d/auditApp123456789/view"
                }
                """;
        mockMvc.perform(post("/api/caretaker/applications")
                        .header("Authorization", "Bearer " + customer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationPayload))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/caretaker/applications")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationPayload))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/caretaker/applications")
                        .header("Authorization", "Bearer " + approvedCaretaker.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationPayload))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/caretaker/applications")
                        .header("Authorization", "Bearer " + unverifiedCaretaker.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationPayload))
                .andExpect(status().isCreated());

        // ------------------------------------------------------------------
        // 3. Submit Post-Approval Certification: Customer=No, Unverified=No, Approved=Yes, Admin=No
        // ------------------------------------------------------------------
        String certPayload = """
                {
                  "title": "ICU Critical Care Certificate",
                  "issuingOrganization": "BSMMU Dhaka",
                  "documentDriveLink": "https://drive.google.com/file/d/auditCert123456789/view"
                }
                """;
        mockMvc.perform(post("/api/caretaker/certifications")
                        .header("Authorization", "Bearer " + customer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(certPayload))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/caretaker/certifications")
                        .header("Authorization", "Bearer " + unverifiedCaretaker.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(certPayload))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/caretaker/certifications")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(certPayload))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/caretaker/certifications")
                        .header("Authorization", "Bearer " + approvedCaretaker.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(certPayload))
                .andExpect(status().isCreated());

        // ------------------------------------------------------------------
        // 4. Approve/Reject Applications & Admin Moderation: Admin=Yes, all others=No(403)
        // ------------------------------------------------------------------
        String[] adminOnlyEndpoints = {
                "/api/admin/applications",
                "/api/admin/certifications",
                "/api/admin/users",
                "/api/reviews/admin"
        };
        for (String endpoint : adminOnlyEndpoints) {
            mockMvc.perform(get(endpoint).header("Authorization", "Bearer " + customer.token()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(endpoint).header("Authorization", "Bearer " + unverifiedCaretaker.token()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(endpoint).header("Authorization", "Bearer " + approvedCaretaker.token()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(endpoint).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }

        // ------------------------------------------------------------------
        // 5. Edit Profile, Rate & Availability: Customer=No, Unverified=Locked(403), Approved=Yes, Admin=Moderation
        // ------------------------------------------------------------------
        String profileUpdatePayload = """
                {
                  "bio": "Experienced ICU and post-op nurse in Dhaka.",
                  "locationArea": "Gulshan, Dhaka",
                  "rateAmount": 1600,
                  "rateType": "DAILY",
                  "available": true
                }
                """;
        mockMvc.perform(put("/api/caretaker/profile/me")
                        .header("Authorization", "Bearer " + customer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileUpdatePayload))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/caretaker/profile/me")
                        .header("Authorization", "Bearer " + unverifiedCaretaker.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileUpdatePayload))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/caretaker/profile/me")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileUpdatePayload))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/caretaker/profile/me")
                        .header("Authorization", "Bearer " + approvedCaretaker.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileUpdatePayload))
                .andExpect(status().isOk());
        // Admin moderation of caretaker availability works cleanly
        mockMvc.perform(patch("/api/admin/users/caretakers/" + approvedCaretaker.userId() + "/availability")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\": true}"))
                .andExpect(status().isOk());

        // ------------------------------------------------------------------
        // 6. Unlock Contacts & Send Hire Request: Customer=Yes, all others=No(403)
        // ------------------------------------------------------------------
        mockMvc.perform(post("/api/contacts/unlock/" + approvedCaretaker.userId())
                        .header("Authorization", "Bearer " + unverifiedCaretaker.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/contacts/unlock/" + approvedCaretaker.userId())
                        .header("Authorization", "Bearer " + secondApprovedCaretaker.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/contacts/unlock/" + approvedCaretaker.userId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/contacts/unlock/" + approvedCaretaker.userId())
                        .header("Authorization", "Bearer " + customer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caretakerMobileNumber").value("01399000004"));

        // Unverified caretaker cannot list negotiation chat rooms
        mockMvc.perform(get("/api/chats/rooms")
                        .header("Authorization", "Bearer " + unverifiedCaretaker.token()))
                .andExpect(status().isForbidden());

        Instant start = Instant.now().plus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(4, ChronoUnit.HOURS);
        String hirePayload = """
                {
                  "caretakerUserId": %d,
                  "agreedRate": 1600,
                  "rateType": "FIXED",
                  "scheduledStartTime": "%s",
                  "scheduledEndTime": "%s",
                  "notes": "Square Hospital Cabin 302"
                }
                """.formatted(approvedCaretaker.userId(), start, end);

        mockMvc.perform(post("/api/hires")
                        .header("Authorization", "Bearer " + unverifiedCaretaker.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hirePayload))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/hires")
                        .header("Authorization", "Bearer " + secondApprovedCaretaker.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hirePayload))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/hires")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hirePayload))
                .andExpect(status().isForbidden());

        MvcResult createdHire = mockMvc.perform(post("/api/hires")
                        .header("Authorization", "Bearer " + customer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hirePayload))
                .andExpect(status().isCreated())
                .andReturn();
        long contractId = objectMapper.readTree(createdHire.getResponse().getContentAsString()).get("id").asLong();

        // ------------------------------------------------------------------
        // 7. Accept / Decline Hire Request: Assigned Approved Caretaker=Yes, others=No(403)
        // ------------------------------------------------------------------
        mockMvc.perform(post("/api/hires/" + contractId + "/accept")
                        .header("Authorization", "Bearer " + customer.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/hires/" + contractId + "/accept")
                        .header("Authorization", "Bearer " + unverifiedCaretaker.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/hires/" + contractId + "/accept")
                        .header("Authorization", "Bearer " + secondApprovedCaretaker.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/hires/" + contractId + "/accept")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        // Assigned approved caretaker accepts cleanly
        mockMvc.perform(post("/api/hires/" + contractId + "/accept")
                        .header("Authorization", "Bearer " + approvedCaretaker.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        // ------------------------------------------------------------------
        // 8. Cancel Contract + IDOR isolation + Admin Override
        // ------------------------------------------------------------------
        String cancelBody = "{\"reason\": \"Emergency schedule change\"}";
        mockMvc.perform(post("/api/hires/" + contractId + "/cancel")
                        .header("Authorization", "Bearer " + outsiderCustomer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/hires/" + contractId + "/cancel")
                        .header("Authorization", "Bearer " + unverifiedCaretaker.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/hires/" + contractId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Admin safety override\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // ------------------------------------------------------------------
        // 9. Reviews & Disputes Gate Checks for Unverified Caretaker / Cross-Role
        // ------------------------------------------------------------------
        mockMvc.perform(get("/api/reviews/pending")
                        .header("Authorization", "Bearer " + unverifiedCaretaker.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reviews/pending")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reviews/customer/" + customer.userId())
                        .header("Authorization", "Bearer " + customer.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reviews/customer/" + customer.userId())
                        .header("Authorization", "Bearer " + unverifiedCaretaker.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reviews/customer/" + customer.userId())
                        .header("Authorization", "Bearer " + approvedCaretaker.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/disputes")
                        .header("Authorization", "Bearer " + unverifiedCaretaker.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/disputes/contract/" + contractId + "/join")
                        .header("Authorization", "Bearer " + customer.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/disputes/contract/" + contractId + "/resolve")
                        .header("Authorization", "Bearer " + approvedCaretaker.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminResolutionNote\": \"Unauthorized resolution attempt\"}"))
                .andExpect(status().isForbidden());
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

    private AuthInfo registerCustomer(String fullName, String mobile, String address) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "%s",
                                  "mobileNumber": "%s",
                                  "address": "%s",
                                  "password": "Password123!"
                                }
                                """.formatted(fullName, mobile, address)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthInfo(json.get("accessToken").asText(), json.get("user").get("id").asLong());
    }

    private AuthInfo registerCaretaker(String fullName, String mobile, String address) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register/caretaker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "%s",
                                  "mobileNumber": "%s",
                                  "address": "%s",
                                  "password": "Password123!"
                                }
                                """.formatted(fullName, mobile, address)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthInfo(json.get("accessToken").asText(), json.get("user").get("id").asLong());
    }

    private void approveCaretaker(String caretakerToken, String adminToken, String qualification) throws Exception {
        MvcResult submitResult = mockMvc.perform(post("/api/caretaker/applications")
                        .header("Authorization", "Bearer " + caretakerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dob": "1993-02-10",
                                  "qualification": "%s",
                                  "experience": "5 years hospital care",
                                  "documentsDriveLink": "https://drive.google.com/file/d/rbacVerify987654321/view"
                                }
                                """.formatted(qualification)))
                .andExpect(status().isCreated())
                .andReturn();
        long appId = objectMapper.readTree(submitResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/admin/applications/" + appId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    private record AuthInfo(String token, long userId) {
    }
}
