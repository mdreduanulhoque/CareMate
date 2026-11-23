package com.carebridge.auth;

import com.carebridge.user.User;
import com.carebridge.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void customerCanRegisterWithFourRequiredFieldsAndAccessMeEndpoint() throws Exception {
        String payload = """
                {
                  "fullName": "Nusrat Jahan",
                  "mobileNumber": "+880 1711-223344",
                  "address": "Dhanmondi, Dhaka",
                  "password": "StrongPassword123!"
                }
                """;

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.mobileNumber").value("01711223344"))
                .andExpect(jsonPath("$.user.email").isEmpty())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andReturn();

        JsonNode json = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String token = json.get("accessToken").asText();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nusrat Jahan"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        // Duplicate mobile in local format must return 409 Conflict
        String duplicatePayload = """
                {
                  "fullName": "Duplicate User",
                  "mobileNumber": "01711223344",
                  "address": "Mirpur, Dhaka",
                  "password": "AnotherPassword123!"
                }
                """;
        mockMvc.perform(post("/api/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicatePayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // Customer must be blocked from Admin endpoints
        mockMvc.perform(get("/api/admin/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void caretakerRegistrationCannotEscalateToAdminRole() throws Exception {
        String payload = """
                {
                  "fullName": "Salma Khatun",
                  "mobileNumber": "01811223344",
                  "address": "Uttara, Dhaka",
                  "email": "salma.nurse@example.com",
                  "password": "NursePassword123!",
                  "role": "ADMIN"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/auth/register/caretaker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CARETAKER"))
                .andExpect(jsonPath("$.user.email").value("salma.nurse@example.com"))
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/admin/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginSupportsFormattedMobileAndRejectsInvalidCredentialsGenerically() throws Exception {
        String registerPayload = """
                {
                  "fullName": "Kamrul Hasan",
                  "mobileNumber": "01911223344",
                  "address": "Gulshan, Dhaka",
                  "password": "MySecretPass123!"
                }
                """;
        mockMvc.perform(post("/api/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated());

        // Login with +880 variant succeeds
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobileNumber": "+8801911223344",
                                  "password": "MySecretPass123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // Wrong password returns generic 401
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobileNumber": "01911223344",
                                  "password": "WrongPassword!"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid mobile number or password."));
    }

    @Test
    void deactivatedUserCannotUseExistingJwtOrLogIn() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Blocked User",
                                  "mobileNumber": "01611223344",
                                  "address": "Banani, Dhaka",
                                  "password": "ValidPassword123!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        User user = userRepository.findByMobileNumber("01611223344").orElseThrow();
        user.setActive(false);
        userRepository.save(user);

        // Existing token is immediately rejected
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        // Login returns 403 Forbidden
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobileNumber": "01611223344",
                                  "password": "ValidPassword123!"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void seededProtectedAdminCanAuthenticateAndAccessAdminEndpoint() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobileNumber": "01700000000",
                                  "password": "AdminTestPass123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn();

        String adminToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();
        assertThat(adminToken).isNotBlank();

        mockMvc.perform(get("/api/admin/status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"));
    }

    @Test
    void unauthenticatedRequestsAndValidationErrorsReturnStructuredJson() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(post("/api/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "A",
                                  "mobileNumber": "01711000000",
                                  "address": "D",
                                  "email": "not-an-email",
                                  "password": "123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());
    }
}
