package br.com.ateliedigital.accounts.controller;

import br.com.ateliedigital.accounts.dto.AccountRequests.CompleteAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountRequests.CreateAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountRequests.UpdateAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountResponses.AccountResponse;
import br.com.ateliedigital.accounts.dto.AddressRequests.AddressRequest;
import br.com.ateliedigital.accounts.dto.AddressResponses.AddressResponse;
import br.com.ateliedigital.accounts.mapper.AccountMapper;
import br.com.ateliedigital.accounts.service.AccountService;
import br.com.ateliedigital.exceptions.GlobalExceptionHandler;
import br.com.ateliedigital.exceptions.ResourceNotFoundException;
import br.com.ateliedigital.exceptions.RuleConflit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    private static final String VALID_CPF = "52998224725";

    @Mock
    private AccountService accountService;
    @Mock
    private AccountMapper mapper;

    @InjectMocks
    private AccountController controller;

    private MockMvc mockMvc;
    private JwtAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(jsonMapper))
                .build();

        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), Map.of("sub", UUID.randomUUID().toString()));
        authentication = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
    }

    @AfterEach
    void tearDown() {
        // Os mocks sao recriados a cada teste pelo MockitoExtension; nada a limpar manualmente.
    }

    // ---------------------------------------------------------------- create

    @Test
    @DisplayName("Account created with success returns 201 and Location")
    void create() throws Exception {
        AccountResponse response = response();
        when(accountService.create(createRequest())).thenReturn(response);

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(VALID_CPF, "1990-05-20")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/accounts/" + response.id()))
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.first_name").value("Maria"))
                .andExpect(jsonPath("$.email").value("maria@example.com"));
    }

    @Test
    @DisplayName("Account not created because CPF is invalid")
    void createFailsWhenCpfIsInvalid() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("11111111111", "1990-05-20")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("cpf must be a valid CPF with 11 digits"))
                .andExpect(jsonPath("$.path").value("/api/accounts"));

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("Account not created because date of birth is not a past date")
    void createFailsWhenDateOfBirthIsInTheFuture() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(VALID_CPF, LocalDate.now().plusDays(1).toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("date_of_birth must be a past date"));

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("Account not created because date of birth is malformed")
    void createFailsWhenDateOfBirthIsMalformed() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(VALID_CPF, "20/05/1990")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("Account not created because body is missing")
    void createFailsWhenBodyIsMissing() throws Exception {
        mockMvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or unreadable request body"));

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("Account not created because CPF or Email is registered")
    void createFailsWhenAccountIsAlreadyRegistered() throws Exception {
        when(accountService.create(createRequest()))
                .thenThrow(new RuleConflit("CPF or email is already registered"));

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(VALID_CPF, "1990-05-20")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("CPF or email is already registered"));
    }

    // ------------------------------------------------------------ getCurrent

    @Test
    @DisplayName("Current account returned with success")
    void getCurrent() throws Exception {
        AccountResponse response = response();
        when(accountService.getCurrentResponse(authentication)).thenReturn(response);

        mockMvc.perform(get("/api/accounts/me").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.cpf").value(VALID_CPF))
                .andExpect(jsonPath("$.date_of_birth").value("1990-05-20"))
                .andExpect(jsonPath("$.addresses.length()").value(1))
                .andExpect(jsonPath("$.addresses[0].zip_code").value("13000000"));
    }

    @Test
    @DisplayName("Current account not returned because account does not exist")
    void getCurrentFailsWhenAccountDoesNotExist() throws Exception {
        when(accountService.getCurrentResponse(authentication))
                .thenThrow(new ResourceNotFoundException("Account not found"));

        mockMvc.perform(get("/api/accounts/me").principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    // -------------------------------------------------------- completeCurrent

    @Test
    @DisplayName("Account completed with success")
    void completeCurrent() throws Exception {
        AccountResponse response = response();
        when(accountService.completeCurrent(eq(authentication), eq(completeRequest()))).thenReturn(response);

        mockMvc.perform(put("/api/accounts/me/complete")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeJson(addressJson())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.bio").value("bio"));
    }

    @Test
    @DisplayName("Account not completed because address is missing")
    void completeCurrentFailsWhenAddressIsMissing() throws Exception {
        mockMvc.perform(put("/api/accounts/me/complete")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeJson("null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Campo obrigatório"));

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("Account not completed because address is invalid")
    void completeCurrentFailsWhenAddressIsInvalid() throws Exception {
        String invalidAddress = """
                {"zip_code": "13000000", "street": " ", "number": "20", "neighborhood": "Bairro",
                 "city": "Campinas", "state": "SP", "main": true}""";

        mockMvc.perform(put("/api/accounts/me/complete")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeJson(invalidAddress)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("Account not completed because CPF is registered")
    void completeCurrentFailsWhenCpfIsAlreadyRegistered() throws Exception {
        when(accountService.completeCurrent(any(), any())).thenThrow(new RuleConflit("CPF is already registered"));

        mockMvc.perform(put("/api/accounts/me/complete")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeJson(addressJson())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("CPF is already registered"));
    }

    // --------------------------------------------------------- updateCurrent

    @Test
    @DisplayName("Account updated with success")
    void updateCurrent() throws Exception {
        AccountResponse response = response();
        UpdateAccountRequest request = new UpdateAccountRequest("Ana", null, null, null, null, "new bio");
        when(accountService.updateCurrent(eq(authentication), eq(request))).thenReturn(response);

        mockMvc.perform(patch("/api/accounts/me")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"first_name\": \"Ana\", \"bio\": \"new bio\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(response.id().toString()));
    }

    @Test
    @DisplayName("Account not updated because first name is blank")
    void updateCurrentFailsWhenFieldIsBlank() throws Exception {
        mockMvc.perform(patch("/api/accounts/me")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"first_name\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("first_name must not be blank"));

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("Account not updated because Email is invalid")
    void updateCurrentFailsWhenEmailIsInvalid() throws Exception {
        mockMvc.perform(patch("/api/accounts/me")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("email must be a valid e-mail"));

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("Account not updated because Email is registered")
    void updateCurrentFailsWhenEmailIsAlreadyRegistered() throws Exception {
        when(accountService.updateCurrent(any(), any())).thenThrow(new RuleConflit("Email is already registered"));

        mockMvc.perform(patch("/api/accounts/me")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"taken@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    // --------------------------------------------------------------- helpers

    private static CreateAccountRequest createRequest() {
        return new CreateAccountRequest("Maria", "Silva", "11999999999",
                LocalDate.of(1990, 5, 20), VALID_CPF, "maria@example.com");
    }

    private static CompleteAccountRequest completeRequest() {
        AddressRequest address = new AddressRequest("13000000", "Rua A", "20", "Apto 1", "Bairro", "Campinas", "SP", true);
        return new CompleteAccountRequest("Maria", "Silva", "11999999999",
                LocalDate.of(1990, 5, 20), VALID_CPF, "bio", address);
    }

    private static AccountResponse response() {
        AddressResponse address = new AddressResponse(UUID.randomUUID(), "13000000", "Rua A", "20", "Apto 1",
                "Bairro", "Campinas", "SP", true);
        return new AccountResponse(UUID.randomUUID(), UUID.randomUUID(), "Maria", "Silva", "11999999999",
                LocalDate.of(1990, 5, 20), VALID_CPF, "maria@example.com", "bio", List.of(address));
    }

    private static String createJson(String cpf, String dateOfBirth) {
        return """
                {"first_name": "Maria", "last_name": "Silva", "cellphone": "11999999999",
                 "date_of_birth": "%s", "cpf": "%s", "email": "maria@example.com"}""".formatted(dateOfBirth, cpf);
    }

    private static String completeJson(String addressJson) {
        return """
                {"first_name": "Maria", "last_name": "Silva", "cellphone": "11999999999",
                 "date_of_birth": "1990-05-20", "cpf": "%s", "bio": "bio", "address": %s}""".formatted(VALID_CPF, addressJson);
    }

    private static String addressJson() {
        return """
                {"zip_code": "13000000", "street": "Rua A", "number": "20", "complement": "Apto 1",
                 "neighborhood": "Bairro", "city": "Campinas", "state": "SP", "main": true}""";
    }
}
