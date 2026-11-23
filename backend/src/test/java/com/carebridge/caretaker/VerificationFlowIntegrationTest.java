package com.carebridge.caretaker;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CaretakerApplicationRepository applicationRepository;

    @Autowired
    private CaretakerProfileRepository profileRepository;

    @Autowired
    private CaretakerCertificationRepository certificationRepository;

    @MockitoBean
    private DriveLinkProbeClient driveLinkProbeClient;

    @BeforeEach
    void setupMockProbe() {
        when(driveLinkProbeClient.probe(anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            if (url.contains("private-doc")) {
                return new DriveProbeResult(false, "Link requires Google sign-in.");
            }
            return new DriveProbeResult(true, "Publicly reachable (HTTP 200).");
        });
    }

    @Test
    void fullCaretakerApplyRejectReapplyApproveAndPostApprovalCertificationFlow() throws Exception {
        String adminToken = loginAsSeededAdmin();

        // 1. Register new Caretaker (starts unapproved)
        MvcResult caretakerReg = mockMvc.perform(post("/api/auth/register/caretaker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Roksana Begum",
                                  "mobileNumber": "01855112233",
                                  "address": "Mirpur 10, Dhaka",
                                  "password": "NursePassword123!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.caretakerApproved").value(false))
                .andReturn();

        JsonNode regJson = objectMapper.readTree(caretakerReg.getResponse().getContentAsString());
        String caretakerToken = regJson.get("accessToken").asText();
        long caretakerUserId = regJson.get("user").get("id").asLong();

        // Unverified caretaker cannot add post-approval certifications
        mockMvc.perform(post("/api/caretaker/certifications")
                        .header("Authorization", "Bearer " + caretakerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "ICU Nursing Diploma",
                                  "issuingOrganization": "Dhaka Nursing College",
                                  "documentDriveLink": "https://drive.google.com/file/d/cert1/view"
                                }
                                """))
                .andExpect(status().isForbidden());

        // Under-18 DOB and spoofed Drive link are rejected
        mockMvc.perform(post("/api/caretaker/applications")
                        .header("Authorization", "Bearer " + caretakerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dob": "2015-05-01",
                                  "qualification": "Diploma in Nursing",
                                  "documentsDriveLink": "https://drive.google.com/file/d/app1/view"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/caretaker/applications")
                        .header("Authorization", "Bearer " + caretakerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dob": "1995-03-15",
                                  "qualification": "Diploma in Nursing",
                                  "documentsDriveLink": "https://evil.example.com/file/d/app1"
                                }
                                """))
                .andExpect(status().isBadRequest());

        // 2. Submit Application #1 (private link flagged by async worker)
        MvcResult app1Res = mockMvc.perform(post("/api/caretaker/applications")
                        .header("Authorization", "Bearer " + caretakerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dob": "1995-03-15",
                                  "qualification": "BSc in Nursing",
                                  "experience": "3 years ICU ward experience",
                                  "documentsDriveLink": "https://drive.google.com/file/d/private-doc-1/view"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationNumber").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        long app1Id = objectMapper.readTree(app1Res.getResponse().getContentAsString()).get("id").asLong();

        // Wait briefly for @Async drive validation pool to update drive_link_reachable
        CaretakerApplication checkedApp1 = awaitApplicationDriveCheck(app1Id);
        assertThat(checkedApp1.getDriveLinkReachable()).isFalse();
        assertThat(checkedApp1.getDriveCheckNote()).contains("Google sign-in");

        // Duplicate submission while PENDING is blocked (409)
        mockMvc.perform(post("/api/caretaker/applications")
                        .header("Authorization", "Bearer " + caretakerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dob": "1995-03-15",
                                  "qualification": "BSc in Nursing",
                                  "documentsDriveLink": "https://drive.google.com/file/d/public-doc-2/view"
                                }
                                """))
                .andExpect(status().isConflict());

        // 3. Admin rejection requires mandatory non-blank reason
        mockMvc.perform(post("/api/admin/applications/" + app1Id + "/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "rejectionReason": "   " }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/applications/" + app1Id + "/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "rejectionReason": "Google Drive link is private. Please enable Anyone with the link can view." }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value(
                        "Google Drive link is private. Please enable Anyone with the link can view."
                ));

        // 4. Caretaker re-applies as brand-new Application #2
        MvcResult app2Res = mockMvc.perform(post("/api/caretaker/applications")
                        .header("Authorization", "Bearer " + caretakerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dob": "1995-03-15",
                                  "qualification": "BSc in Nursing",
                                  "experience": "3 years ICU ward experience",
                                  "documentsDriveLink": "https://drive.google.com/drive/folders/public-folder-2"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationNumber").value(2))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        long app2Id = objectMapper.readTree(app2Res.getResponse().getContentAsString()).get("id").asLong();
        CaretakerApplication checkedApp2 = awaitApplicationDriveCheck(app2Id);
        assertThat(checkedApp2.getDriveLinkReachable()).isTrue();

        // Admin applicant history preserves BOTH Application #2 and Application #1
        mockMvc.perform(get("/api/admin/applications/user/" + caretakerUserId + "/history")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].applicationNumber").value(2))
                .andExpect(jsonPath("$[1].applicationNumber").value(1))
                .andExpect(jsonPath("$[1].status").value("REJECTED"));

        // 5. Admin approves Application #2 -> unlocks CaretakerProfile
        mockMvc.perform(post("/api/admin/applications/" + app2Id + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(profileRepository.existsByUserId(caretakerUserId)).isTrue();

        // /api/auth/me now reflects caretakerApproved = true
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + caretakerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caretakerApproved").value(true));

        // Approved caretaker cannot re-submit another initial application
        mockMvc.perform(post("/api/caretaker/applications")
                        .header("Authorization", "Bearer " + caretakerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dob": "1995-03-15",
                                  "qualification": "BSc in Nursing",
                                  "documentsDriveLink": "https://drive.google.com/file/d/extra-app/view"
                                }
                                """))
                .andExpect(status().isConflict());

        // 6. Approved caretaker submits post-approval certification (live profile stays uninterrupted)
        MvcResult cert1Res = mockMvc.perform(post("/api/caretaker/certifications")
                        .header("Authorization", "Bearer " + caretakerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Critical Care & ICU Support",
                                  "issuingOrganization": "Bangladesh Nursing Council",
                                  "documentDriveLink": "https://drive.google.com/file/d/public-cert-1/view"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andReturn();

        long cert1Id = objectMapper.readTree(cert1Res.getResponse().getContentAsString()).get("id").asLong();
        CaretakerCertification checkedCert1 = awaitCertificationDriveCheck(cert1Id);
        assertThat(checkedCert1.getDriveLinkReachable()).isTrue();

        // Admin approves certification #1
        mockMvc.perform(post("/api/admin/certifications/" + cert1Id + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("APPROVED"));

        // Submit certification #2 and Admin rejects it with mandatory reason; profile remains live
        MvcResult cert2Res = mockMvc.perform(post("/api/caretaker/certifications")
                        .header("Authorization", "Bearer " + caretakerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Elderly Physiotherapy",
                                  "issuingOrganization": "Private Institute",
                                  "documentDriveLink": "https://drive.google.com/file/d/private-doc-cert2/view"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long cert2Id = objectMapper.readTree(cert2Res.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/admin/certifications/" + cert2Id + "/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "rejectionReason": "Certificate scan is blurry and unreadable." }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Certificate scan is blurry and unreadable."));

        assertThat(profileRepository.findByUserId(caretakerUserId).orElseThrow().isAvailable()).isTrue();
    }

    @Test
    void enforcesRoleBoundariesAcrossVerificationEndpoints() throws Exception {
        MvcResult customerReg = mockMvc.perform(post("/api/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Tanvir Ahmed",
                                  "mobileNumber": "01755667788",
                                  "address": "Banani, Dhaka",
                                  "password": "CustomerPass123!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String customerToken = objectMapper.readTree(customerReg.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/caretaker/applications/me")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/applications")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/certifications")
                        .header("Authorization", "Bearer " + customerToken))
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

    private CaretakerApplication awaitApplicationDriveCheck(long applicationId) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            CaretakerApplication app = applicationRepository.findById(applicationId).orElseThrow();
            if (app.getDriveLinkReachable() != null) {
                return app;
            }
            Thread.sleep(50);
        }
        return applicationRepository.findById(applicationId).orElseThrow();
    }

    private CaretakerCertification awaitCertificationDriveCheck(long certificationId) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            CaretakerCertification cert = certificationRepository.findById(certificationId).orElseThrow();
            if (cert.getDriveLinkReachable() != null) {
                return cert;
            }
            Thread.sleep(50);
        }
        return certificationRepository.findById(certificationId).orElseThrow();
    }
}
