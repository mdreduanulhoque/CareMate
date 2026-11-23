package com.carebridge.chat;

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
class ContactUnlockAndChatRestIntegrationTest {

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
    void contactUnlockAndPersistentNegotiationChatEnforcePrivacyAndRbac() throws Exception {
        String adminToken = loginAsSeededAdmin();
        AuthInfo customerA = registerCustomer("Sadia Rahman", "01941000001", "Dhanmondi, Dhaka");
        AuthInfo customerIntruder = registerCustomer("Intruder Relative", "01941000002", "Mirpur, Dhaka");

        AuthInfo unverifiedCaretaker = registerCaretaker("Pending Attendant", "01941000003", "Banani, Dhaka");
        AuthInfo approvedCaretaker = registerCaretaker("Nurse Salma", "01941000004", "Dhanmondi, Dhaka");
        approveCaretaker(approvedCaretaker.token(), adminToken, "BSc in Nursing");

        AuthInfo otherCaretaker = registerCaretaker("Nurse Ripa", "01941000005", "Uttara, Dhaka");
        approveCaretaker(otherCaretaker.token(), adminToken, "Diploma in Nursing");

        // Section 2.4 RBAC: Only CUSTOMER may unlock contacts
        mockMvc.perform(post("/api/contacts/unlock/" + approvedCaretaker.userId())
                        .header("Authorization", "Bearer " + unverifiedCaretaker.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/contacts/unlock/" + approvedCaretaker.userId())
                        .header("Authorization", "Bearer " + otherCaretaker.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/contacts/unlock/" + approvedCaretaker.userId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        // Cannot unlock unverified caretaker
        mockMvc.perform(post("/api/contacts/unlock/" + unverifiedCaretaker.userId())
                        .header("Authorization", "Bearer " + customerA.token()))
                .andExpect(status().isNotFound());

        // Before unlock: GET unlocked status returns 404 (read-only never unlocks), and public detail hides phone
        mockMvc.perform(get("/api/contacts/unlock/" + approvedCaretaker.userId())
                        .header("Authorization", "Bearer " + customerA.token()))
                .andExpect(status().isNotFound());

        MvcResult beforeDetail = mockMvc.perform(get("/api/feed/caretakers/" + approvedCaretaker.userId())
                        .header("Authorization", "Bearer " + customerA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactUnlocked").value(false))
                .andReturn();
        assertThat(beforeDetail.getResponse().getContentAsString()).doesNotContain("01941000004");

        // Accessing negotiation room BEFORE contact unlock is strictly forbidden (403)
        String expectedRoomId = "NEGOTIATION:" + customerA.userId() + ":" + approvedCaretaker.userId();
        mockMvc.perform(get("/api/chats/negotiation/" + expectedRoomId)
                        .header("Authorization", "Bearer " + customerA.token()))
                .andExpect(status().isForbidden());

        // Customer unlocks contact -> reveals phone number & deterministic room ID
        MvcResult firstUnlock = mockMvc.perform(post("/api/contacts/unlock/" + approvedCaretaker.userId())
                        .header("Authorization", "Bearer " + customerA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(expectedRoomId))
                .andExpect(jsonPath("$.caretakerMobileNumber").value("01941000004"))
                .andExpect(jsonPath("$.newlyUnlocked").value(true))
                .andReturn();
        long unlockId = objectMapper.readTree(firstUnlock.getResponse().getContentAsString()).get("unlockId").asLong();

        // Repeat unlock is idempotent
        mockMvc.perform(post("/api/contacts/unlock/" + approvedCaretaker.userId())
                        .header("Authorization", "Bearer " + customerA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unlockId").value(unlockId))
                .andExpect(jsonPath("$.roomId").value(expectedRoomId))
                .andExpect(jsonPath("$.newlyUnlocked").value(false));

        // Subsequent GET unlocked status returns the revealed contact
        mockMvc.perform(get("/api/contacts/unlock/" + approvedCaretaker.userId())
                        .header("Authorization", "Bearer " + customerA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caretakerMobileNumber").value("01941000004"));

        // Public detail now reflects contactUnlocked=true while STILL omitting mobileNumber
        MvcResult afterDetail = mockMvc.perform(get("/api/feed/caretakers/" + approvedCaretaker.userId())
                        .header("Authorization", "Bearer " + customerA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactUnlocked").value(true))
                .andReturn();
        assertThat(afterDetail.getResponse().getContentAsString()).doesNotContain("01941000004");

        // Customer sends message via REST; caretaker replies; history persists chronologically
        mockMvc.perform(post("/api/chats/negotiation/" + expectedRoomId + "/messages")
                        .header("Authorization", "Bearer " + customerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "  My father is at Square Hospital Ward 4B. Available tonight?  " }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(expectedRoomId))
                .andExpect(jsonPath("$.senderId").value(customerA.userId()))
                .andExpect(jsonPath("$.content").value("My father is at Square Hospital Ward 4B. Available tonight?"));

        mockMvc.perform(post("/api/chats/negotiation/" + expectedRoomId + "/messages")
                        .header("Authorization", "Bearer " + approvedCaretaker.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "Yes, I am available from 8 PM for 12 hours at 2200 BDT." }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderId").value(approvedCaretaker.userId()));

        // Blank & oversized (>2000) messages are rejected with 400
        mockMvc.perform(post("/api/chats/negotiation/" + expectedRoomId + "/messages")
                        .header("Authorization", "Bearer " + customerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "   " }
                                """))
                .andExpect(status().isBadRequest());

        String oversized = "a".repeat(2001);
        mockMvc.perform(post("/api/chats/negotiation/" + expectedRoomId + "/messages")
                        .header("Authorization", "Bearer " + customerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + oversized + "\"}"))
                .andExpect(status().isBadRequest());

        // Both parties see the room in /api/chats/rooms with last message preview
        mockMvc.perform(get("/api/chats/rooms")
                        .header("Authorization", "Bearer " + customerA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomId").value(expectedRoomId))
                .andExpect(jsonPath("$[0].counterpartMobileNumber").value("01941000004"))
                .andExpect(jsonPath("$[0].lastMessageContent")
                        .value("Yes, I am available from 8 PM for 12 hours at 2200 BDT."));

        mockMvc.perform(get("/api/chats/rooms")
                        .header("Authorization", "Bearer " + approvedCaretaker.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomId").value(expectedRoomId))
                .andExpect(jsonPath("$[0].counterpartName").value("Sadia Rahman"));

        // Room detail returns chronological messages
        mockMvc.perform(get("/api/chats/negotiation/" + expectedRoomId)
                        .header("Authorization", "Bearer " + approvedCaretaker.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].senderId").value(customerA.userId()))
                .andExpect(jsonPath("$.messages[1].senderId").value(approvedCaretaker.userId()));

        // IDOR protection: intruder customer, other caretaker, unverified caretaker, and admin are all denied (403)
        mockMvc.perform(get("/api/chats/negotiation/" + expectedRoomId)
                        .header("Authorization", "Bearer " + customerIntruder.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/chats/negotiation/" + expectedRoomId + "/messages")
                        .header("Authorization", "Bearer " + customerIntruder.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "Snooping" }
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/chats/negotiation/" + expectedRoomId)
                        .header("Authorization", "Bearer " + otherCaretaker.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/chats/rooms")
                        .header("Authorization", "Bearer " + unverifiedCaretaker.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/chats/rooms")
                        .header("Authorization", "Bearer " + adminToken))
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
                                  "dob": "1992-05-12",
                                  "qualification": "%s",
                                  "experience": "5 years hospital bedside care",
                                  "documentsDriveLink": "https://drive.google.com/file/d/public-doc-phase4/view"
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
