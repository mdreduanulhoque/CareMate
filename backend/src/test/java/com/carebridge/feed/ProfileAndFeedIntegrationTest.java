package com.carebridge.feed;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileAndFeedIntegrationTest {

    private static final byte[] VALID_PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DriveLinkProbeClient driveLinkProbeClient;

    @BeforeEach
    void setupProbe() {
        when(driveLinkProbeClient.probe(anyString()))
                .thenReturn(new DriveProbeResult(true, "Publicly reachable (HTTP 200)."));
    }

    @Test
    void profileManagementAvatarUploadFeedFilteringAndPrivacyProtectionWorkEndToEnd() throws Exception {
        String adminToken = loginAsSeededAdmin();
        String customerToken = registerCustomer("Farhana Yasmin", "01733445566", "Dhanmondi, Dhaka");

        // Unverified caretaker is blocked from profile editor AND Hunters Feed (Section 2.4 RBAC)
        AuthInfo unverified = registerCaretaker("Unverified Nurse", "01833001122", "Mirpur, Dhaka");
        mockMvc.perform(get("/api/caretaker/profile/me")
                        .header("Authorization", "Bearer " + unverified.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/feed/caretakers")
                        .header("Authorization", "Bearer " + unverified.token()))
                .andExpect(status().isForbidden());

        // Create and approve Caretaker A (Available, Hourly 450 BDT, Dhanmondi, approved ICU cert + pending secret cert)
        AuthInfo nurseA = registerCaretaker("Fatema Akter", "01844556677", "Dhanmondi, Dhaka");
        approveCaretaker(nurseA.token(), adminToken, "BSc in Nursing", "4 years ICU care");

        mockMvc.perform(put("/api/caretaker/profile/me")
                        .header("Authorization", "Bearer " + nurseA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": "Dedicated ICU and post-surgery bedside nurse.",
                                  "locationArea": "Dhanmondi, Dhaka",
                                  "rateAmount": 450.00,
                                  "rateType": "HOURLY",
                                  "available": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateAmount").value(450.00))
                .andExpect(jsonPath("$.rateType").value("HOURLY"))
                .andExpect(jsonPath("$.available").value(true));

        // Upload valid PNG avatar and verify it is served safely
        MockMultipartFile validPng = new MockMultipartFile("file", "fatema.png", "image/png", VALID_PNG_BYTES);
        MvcResult avatarRes = mockMvc.perform(multipart("/api/caretaker/profile/avatar")
                        .file(validPng)
                        .header("Authorization", "Bearer " + nurseA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").isNotEmpty())
                .andReturn();

        String avatarUrl = objectMapper.readTree(avatarRes.getResponse().getContentAsString())
                .get("profileImageUrl").asText();

        mockMvc.perform(get(avatarUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        // Spoofed file upload is rejected with 400
        MockMultipartFile spoofed = new MockMultipartFile("file", "fake.jpg", "image/jpeg", "not-an-image".getBytes());
        mockMvc.perform(multipart("/api/caretaker/profile/avatar")
                        .file(spoofed)
                        .header("Authorization", "Bearer " + nurseA.token()))
                .andExpect(status().isBadRequest());

        // Add one APPROVED cert and one PENDING cert (pending must NOT leak into feed/search)
        long approvedCertId = submitCert(nurseA.token(), "Cardiac ICU Care", "Dhaka Nursing Council");
        mockMvc.perform(post("/api/admin/certifications/" + approvedCertId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        submitCert(nurseA.token(), "SecretPendingNeuroCert", "Some Institute");

        // Create and approve Caretaker B (Unavailable, Daily 2200 BDT, Uttara)
        AuthInfo nurseB = registerCaretaker("Jannatul Ferdous", "01844556688", "Uttara, Dhaka");
        approveCaretaker(nurseB.token(), adminToken, "Diploma in Nursing", "Elderly care specialist");
        mockMvc.perform(put("/api/caretaker/profile/me")
                        .header("Authorization", "Bearer " + nurseB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": "Gentle elderly ward attendant.",
                                  "locationArea": "Uttara, Dhaka",
                                  "rateAmount": 2200.00,
                                  "rateType": "DAILY",
                                  "available": true
                                }
                                """))
                .andExpect(status().isOk());

        // Toggle Caretaker B unavailable via quick PATCH endpoint
        mockMvc.perform(patch("/api/caretaker/profile/availability")
                        .header("Authorization", "Bearer " + nurseB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "available": false }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        // Feed default (availableOnly=true): returns Fatema, excludes unavailable Jannatul and Unverified Nurse
        MvcResult feedDefaultRes = mockMvc.perform(get("/api/feed/caretakers")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.fullName=='Fatema Akter')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.fullName=='Jannatul Ferdous')]").isEmpty())
                .andExpect(jsonPath("$.content[?(@.fullName=='Unverified Nurse')]").isEmpty())
                .andReturn();

        // STRICT PRIVACY: phone number must NEVER leak in feed payload
        String feedBody = feedDefaultRes.getResponse().getContentAsString();
        assertThat(feedBody).doesNotContain("01844556677");
        assertThat(feedBody).doesNotContain("mobileNumber");
        assertThat(feedBody).doesNotContain("SecretPendingNeuroCert");
        assertThat(feedBody).contains("Cardiac ICU Care");

        // Filtering with availableOnly=false & rateType=DAILY & area=Uttara finds Jannatul
        mockMvc.perform(get("/api/feed/caretakers")
                        .param("availableOnly", "false")
                        .param("rateType", "DAILY")
                        .param("maxBudget", "2500")
                        .param("area", "Uttara")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Jannatul Ferdous"));

        // Keyword search finds approved cert title, does NOT find pending cert title
        mockMvc.perform(get("/api/feed/caretakers")
                        .param("keyword", "Cardiac ICU")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fullName").value("Fatema Akter"));

        mockMvc.perform(get("/api/feed/caretakers")
                        .param("keyword", "SecretPendingNeuroCert")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        // Public detail endpoint omits phone number & only exposes approved certs
        MvcResult detailRes = mockMvc.perform(get("/api/feed/caretakers/" + nurseA.userId())
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Fatema Akter"))
                .andExpect(jsonPath("$.verifiedCertifications.length()").value(1))
                .andExpect(jsonPath("$.verifiedCertifications[0].title").value("Cardiac ICU Care"))
                .andExpect(jsonPath("$.contactUnlocked").value(false))
                .andReturn();

        assertThat(detailRes.getResponse().getContentAsString()).doesNotContain("01844556677");
        assertThat(detailRes.getResponse().getContentAsString()).doesNotContain("mobileNumber");

        // Approved caretaker CAN browse Hunters Feed (Read-Only per Section 2.4)
        mockMvc.perform(get("/api/feed/caretakers")
                        .header("Authorization", "Bearer " + nurseA.token()))
                .andExpect(status().isOk());
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

    private String registerCustomer(String name, String mobile, String address) throws Exception {
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
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
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

    private void approveCaretaker(
            String caretakerToken,
            String adminToken,
            String qualification,
            String experience
    ) throws Exception {
        MvcResult appRes = mockMvc.perform(post("/api/caretaker/applications")
                        .header("Authorization", "Bearer " + caretakerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dob": "1993-08-20",
                                  "qualification": "%s",
                                  "experience": "%s",
                                  "documentsDriveLink": "https://drive.google.com/file/d/public-app-doc/view"
                                }
                                """.formatted(qualification, experience)))
                .andExpect(status().isCreated())
                .andReturn();
        long appId = objectMapper.readTree(appRes.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/admin/applications/" + appId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    private long submitCert(String caretakerToken, String title, String org) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/caretaker/certifications")
                        .header("Authorization", "Bearer " + caretakerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "issuingOrganization": "%s",
                                  "documentDriveLink": "https://drive.google.com/file/d/public-cert-doc/view"
                                }
                                """.formatted(title, org)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private record AuthInfo(String token, long userId) {
    }
}
