package com.carebridge.booking;

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
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

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
class HiringFlowIntegrationTest {

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
    void hiringLifecycleDailyCapEarlyTerminationAndRbacWorkEndToEnd() throws Exception {
        String adminToken = loginAsSeededAdmin();
        AuthInfo customerA = registerCustomer("Mahmudul Hasan", "01551000001", "Dhanmondi, Dhaka");
        AuthInfo customerIntruder = registerCustomer("Nosy Relative", "01551000002", "Mirpur, Dhaka");

        AuthInfo unverifiedCaretaker = registerCaretaker("Unverified Nurse", "01551000003", "Banani, Dhaka");
        AuthInfo nurseA = registerCaretaker("Nurse Sharmin", "01551000004", "Dhanmondi, Dhaka");
        approveCaretaker(nurseA.token(), adminToken, "BSc in Nursing");

        AuthInfo nurseOther = registerCaretaker("Nurse Lima", "01551000005", "Uttara, Dhaka");
        approveCaretaker(nurseOther.token(), adminToken, "Diploma in Nursing");

        LocalDate tomorrowDhaka = LocalDate.now(BookingScheduleValidator.DHAKA_ZONE).plusDays(2);
        Instant morningStart = dhaka(tomorrowDhaka, 8, 0);
        Instant morningEnd = dhaka(tomorrowDhaka, 12, 0);
        Instant eveningStart = dhaka(tomorrowDhaka, 16, 0);
        Instant eveningEnd = dhaka(tomorrowDhaka, 20, 0);
        Instant lateStart = dhaka(tomorrowDhaka, 21, 0);
        Instant lateEnd = dhaka(tomorrowDhaka, 23, 0);

        // RBAC: Caretaker & Admin cannot create hire requests (Section 2.4)
        mockMvc.perform(post("/api/hires")
                        .header("Authorization", "Bearer " + nurseA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hireJson(nurseOther.userId(), "500.00", "HOURLY", morningStart, morningEnd, null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/hires")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hireJson(nurseA.userId(), "500.00", "HOURLY", morningStart, morningEnd, null)))
                .andExpect(status().isForbidden());

        // Unverified caretaker cannot list hires or view schedules
        mockMvc.perform(get("/api/hires")
                        .header("Authorization", "Bearer " + unverifiedCaretaker.token()))
                .andExpect(status().isForbidden());

        // Customer cannot hire unverified caretaker
        mockMvc.perform(post("/api/hires")
                        .header("Authorization", "Bearer " + customerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hireJson(unverifiedCaretaker.userId(), "500.00", "HOURLY", morningStart, morningEnd, null)))
                .andExpect(status().isNotFound());

        // 1. Customer creates Morning Hire Request WITHOUT unlocking contact -> phone stays hidden!
        MvcResult morningRes = mockMvc.perform(post("/api/hires")
                        .header("Authorization", "Bearer " + customerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hireJson(
                                nurseA.userId(),
                                "550.00",
                                "HOURLY",
                                morningStart,
                                morningEnd,
                                "Square Hospital, Cabin 502"
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.caretakerMobileNumber").isEmpty())
                .andExpect(jsonPath("$.notes").value("Square Hospital, Cabin 502"))
                .andReturn();
        long morningId = objectMapper.readTree(morningRes.getResponse().getContentAsString()).get("id").asLong();

        // Duplicate overlapping pending request by SAME customer is blocked with 409
        mockMvc.perform(post("/api/hires")
                        .header("Authorization", "Bearer " + customerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hireJson(nurseA.userId(), "550.00", "HOURLY", morningStart, morningEnd, null)))
                .andExpect(status().isConflict());

        // IDOR checks: intruder customer & other caretaker cannot read or accept this contract
        mockMvc.perform(get("/api/hires/" + morningId)
                        .header("Authorization", "Bearer " + customerIntruder.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/hires/" + morningId + "/accept")
                        .header("Authorization", "Bearer " + nurseOther.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/hires/" + morningId + "/accept")
                        .header("Authorization", "Bearer " + customerA.token()))
                .andExpect(status().isForbidden());

        // Caretaker accepts Morning shift -> SCHEDULED
        mockMvc.perform(post("/api/hires/" + morningId + "/accept")
                        .header("Authorization", "Bearer " + nurseA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.canCancel").value(true));

        // Overlapping shift is blocked immediately on create (409)
        mockMvc.perform(post("/api/hires")
                        .header("Authorization", "Bearer " + customerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hireJson(
                                nurseA.userId(),
                                "550.00",
                                "HOURLY",
                                dhaka(tomorrowDhaka, 10, 0),
                                dhaka(tomorrowDhaka, 14, 0),
                                null
                        )))
                .andExpect(status().isConflict());

        // 2. Customer creates Evening shift (2nd non-overlapping shift same Dhaka day) & Caretaker accepts -> SCHEDULED
        MvcResult eveningRes = mockMvc.perform(post("/api/hires")
                        .header("Authorization", "Bearer " + customerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hireJson(nurseA.userId(), "2000.00", "FIXED", eveningStart, eveningEnd, null)))
                .andExpect(status().isCreated())
                .andReturn();
        long eveningId = objectMapper.readTree(eveningRes.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/hires/" + eveningId + "/accept")
                        .header("Authorization", "Bearer " + nurseA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        // Privacy-safe schedule endpoint shows 2/2 booked with ZERO customer PII
        MvcResult scheduleRes = mockMvc.perform(get("/api/hires/caretaker/" + nurseA.userId() + "/schedule")
                        .param("date", tomorrowDhaka.toString())
                        .header("Authorization", "Bearer " + customerIntruder.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsCount").value(2))
                .andExpect(jsonPath("$.dailyCapReached").value(true))
                .andExpect(jsonPath("$.busySlots.length()").value(2))
                .andReturn();
        String scheduleJson = scheduleRes.getResponse().getContentAsString();
        assertThat(scheduleJson).doesNotContain("Mahmudul Hasan");
        assertThat(scheduleJson).doesNotContain("01551000001");

        // 3rd booking on the SAME Dhaka day is blocked with 409 Conflict!
        mockMvc.perform(post("/api/hires")
                        .header("Authorization", "Bearer " + customerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hireJson(nurseA.userId(), "1200.00", "FIXED", lateStart, lateEnd, null)))
                .andExpect(status().isConflict());

        // 3. Cancel Evening shift before start -> immediately frees daily booking slot!
        mockMvc.perform(post("/api/hires/" + eveningId + "/cancel")
                        .header("Authorization", "Bearer " + customerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Relative will stay at hospital in the evening." }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.initiatedByUserId").value(customerA.userId()));

        // Now the 3rd shift on that same Dhaka day succeeds!
        MvcResult lateRes = mockMvc.perform(post("/api/hires")
                        .header("Authorization", "Bearer " + customerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hireJson(nurseA.userId(), "1200.00", "FIXED", lateStart, lateEnd, null)))
                .andExpect(status().isCreated())
                .andReturn();
        long lateId = objectMapper.readTree(lateRes.getResponse().getContentAsString()).get("id").asLong();

        // Caretaker declines late shift -> DECLINED
        mockMvc.perform(post("/api/hires/" + lateId + "/decline")
                        .header("Authorization", "Bearer " + nurseA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Late night travel is not possible for me." }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        // 4. Active Shift + End Service Early flow
        Instant activeStart = Instant.now().minus(2, ChronoUnit.MINUTES);
        Instant activeEnd = Instant.now().plus(6, ChronoUnit.HOURS);

        MvcResult activeCreate = mockMvc.perform(post("/api/hires")
                        .header("Authorization", "Bearer " + customerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hireJson(
                                nurseA.userId(),
                                "2400.00",
                                "DAILY",
                                activeStart,
                                activeEnd,
                                "Immediate ICU bedside shift"
                        )))
                .andExpect(status().isCreated())
                .andReturn();
        long activeId = objectMapper.readTree(activeCreate.getResponse().getContentAsString()).get("id").asLong();

        // Accepting a shift whose start <= now moves it directly to ACTIVE
        mockMvc.perform(post("/api/hires/" + activeId + "/accept")
                        .header("Authorization", "Bearer " + nurseA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.canEndEarly").value(true))
                .andExpect(jsonPath("$.canCancel").value(false));

        // Cannot Cancel an ACTIVE shift (must End Service Early)
        mockMvc.perform(post("/api/hires/" + activeId + "/cancel")
                        .header("Authorization", "Bearer " + customerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Trying to cancel active shift" }
                                """))
                .andExpect(status().isConflict());

        // End Service Early requires mandatory reason
        mockMvc.perform(post("/api/hires/" + activeId + "/end-early")
                        .header("Authorization", "Bearer " + customerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": " " }
                                """))
                .andExpect(status().isBadRequest());

        // Customer triggers End Service Early -> COMPLETED_PENDING_SETTLEMENT + actualEndTime recorded
        mockMvc.perform(post("/api/hires/" + activeId + "/end-early")
                        .header("Authorization", "Bearer " + customerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Doctor approved early discharge today." }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED_PENDING_SETTLEMENT"))
                .andExpect(jsonPath("$.actualEndTime").isNotEmpty())
                .andExpect(jsonPath("$.initiatedByUserId").value(customerA.userId()));

        // Remaining schedule is immediately freed so another shift in the next 3 hours can be booked!
        Instant newStart = Instant.now().plus(15, ChronoUnit.MINUTES);
        Instant newEnd = Instant.now().plus(3, ChronoUnit.HOURS);
        mockMvc.perform(post("/api/hires")
                        .header("Authorization", "Bearer " + customerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hireJson(nurseA.userId(), "1500.00", "FIXED", newStart, newEnd, null)))
                .andExpect(status().isCreated());
    }

    private Instant dhaka(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(
                date.getYear(),
                date.getMonthValue(),
                date.getDayOfMonth(),
                hour,
                minute,
                0,
                0,
                BookingScheduleValidator.DHAKA_ZONE
        ).toInstant();
    }

    private String hireJson(
            long caretakerUserId,
            String rate,
            String rateType,
            Instant start,
            Instant end,
            String notes
    ) {
        String notesField = notes == null ? "null" : "\"" + notes + "\"";
        return """
                {
                  "caretakerUserId": %d,
                  "agreedRate": %s,
                  "rateType": "%s",
                  "scheduledStartTime": "%s",
                  "scheduledEndTime": "%s",
                  "notes": %s
                }
                """.formatted(caretakerUserId, rate, rateType, start.toString(), end.toString(), notesField);
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
                                  "dob": "1993-06-15",
                                  "qualification": "%s",
                                  "experience": "5 years clinical bedside experience",
                                  "documentsDriveLink": "https://drive.google.com/file/d/public-hire-test/view"
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
