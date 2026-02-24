package org.example.reader.controller;

import org.example.reader.model.AccountStateSnapshot;
import org.example.reader.service.AccountAuthAuditService;
import org.example.reader.service.AccountAuthRateLimiter;
import org.example.reader.service.AccountAuthService;
import org.example.reader.service.AccountClaimSyncService;
import org.example.reader.service.AccountMetricsService;
import org.example.reader.service.ReaderProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountAuthService accountAuthService;

    @MockitoBean
    private ReaderProfileService readerProfileService;

    @MockitoBean
    private AccountClaimSyncService accountClaimSyncService;

    @MockitoBean
    private AccountMetricsService accountMetricsService;

    @MockitoBean
    private AccountAuthRateLimiter accountAuthRateLimiter;

    @MockitoBean
    private AccountAuthAuditService accountAuthAuditService;

    @Test
    void status_returnsUnauthenticatedWhenNoSession() throws Exception {
        when(accountAuthService.status(any()))
                .thenReturn(new AccountAuthService.AuthResult(
                        AccountAuthService.ResultStatus.SUCCESS,
                        true,
                        false,
                        null,
                        null
                ));

        mockMvc.perform(get("/api/account/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountAuthEnabled", is(true)))
                .andExpect(jsonPath("$.authenticated", is(false)));
    }

    @Test
    void register_missingFields_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/account/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Email and password are required.")));
    }

    @Test
    void register_emailAlreadyExists_returnsConflict() throws Exception {
        allowRegisterAndLoginRateLimits();
        when(accountAuthService.register(eq("reader@example.com"), eq("password123"), any()))
                .thenReturn(new AccountAuthService.AuthResult(
                        AccountAuthService.ResultStatus.EMAIL_ALREADY_EXISTS,
                        true,
                        false,
                        null,
                        "Email is already registered."
                ));

        mockMvc.perform(post("/api/account/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"reader@example.com","password":"password123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Email is already registered.")));

        verify(accountAuthAuditService).record(eq("register"), eq("email_exists"), any(), eq("reader@example.com"), isNull(), isNull(), isNull());
    }

    @Test
    void register_rolloutRestricted_returnsForbidden() throws Exception {
        allowRegisterAndLoginRateLimits();
        when(accountAuthService.register(eq("reader@example.com"), eq("password123"), any()))
                .thenReturn(new AccountAuthService.AuthResult(
                        AccountAuthService.ResultStatus.ROLLOUT_RESTRICTED,
                        true,
                        false,
                        null,
                        "Account access is currently limited to internal rollout users."
                ));

        mockMvc.perform(post("/api/account/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"reader@example.com","password":"password123"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Account access is currently limited to internal rollout users.")));

        verify(accountAuthAuditService).record(eq("register"), eq("rollout_restricted"), any(), eq("reader@example.com"), isNull(), isNull(), isNull());
    }

    @Test
    void login_invalidCredentials_returnsUnauthorized() throws Exception {
        allowRegisterAndLoginRateLimits();
        when(accountAuthService.login(eq("reader@example.com"), eq("wrong-password"), any()))
                .thenReturn(new AccountAuthService.AuthResult(
                        AccountAuthService.ResultStatus.INVALID_CREDENTIALS,
                        true,
                        false,
                        null,
                        "Invalid email or password."
                ));

        mockMvc.perform(post("/api/account/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"reader@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid email or password.")));
    }

    @Test
    void register_whenAuthDisabled_returnsServiceUnavailable() throws Exception {
        allowRegisterAndLoginRateLimits();
        when(accountAuthService.register(eq("reader@example.com"), eq("password123"), any()))
                .thenReturn(new AccountAuthService.AuthResult(
                        AccountAuthService.ResultStatus.DISABLED,
                        false,
                        false,
                        null,
                        "Account auth is disabled."
                ));

        mockMvc.perform(post("/api/account/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"reader@example.com","password":"password123"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.accountAuthEnabled", is(false)));
    }

    @Test
    void register_rateLimited_returnsTooManyRequestsAndRetryAfter() throws Exception {
        when(accountAuthRateLimiter.checkRegister(any(), eq("reader@example.com")))
                .thenReturn(AccountAuthRateLimiter.RateLimitResult.limited(45, "ip"));

        mockMvc.perform(post("/api/account/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"reader@example.com","password":"password123"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "45"))
                .andExpect(jsonPath("$.message", is("Too many account auth attempts. Please retry shortly.")));

        verify(accountAuthAuditService).record(eq("register"), eq("rate_limited"), any(), eq("reader@example.com"), isNull(), eq(45), eq("ip"));
    }

    @Test
    void login_accountLocked_returnsTooManyRequestsAndRetryAfter() throws Exception {
        allowRegisterAndLoginRateLimits();
        when(accountAuthService.login(eq("reader@example.com"), eq("wrong-password"), any()))
                .thenReturn(new AccountAuthService.AuthResult(
                        AccountAuthService.ResultStatus.ACCOUNT_LOCKED,
                        true,
                        false,
                        null,
                        "Too many failed sign-in attempts. Please try again later.",
                        30
                ));

        mockMvc.perform(post("/api/account/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"reader@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.message", is("Too many failed sign-in attempts. Please try again later.")));
    }

    @Test
    void claimSync_requiresAuthenticatedAccount() throws Exception {
        when(accountAuthService.resolveAuthenticatedPrincipal(any())).thenReturn(java.util.Optional.empty());

        mockMvc.perform(post("/api/account/claim-sync")
                        .contentType("application/json")
                        .content("""
                                {"state":{"favoriteBookIds":["book-1"]}}
                                """))
                .andExpect(status().isUnauthorized());

        verify(accountAuthAuditService).record(eq("claim_sync"), eq("unauthorized"), any(), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void claimSync_returnsMergedState() throws Exception {
        when(accountAuthService.resolveAuthenticatedPrincipal(any()))
                .thenReturn(java.util.Optional.of(new AccountAuthService.AccountPrincipal("user-1", "reader@example.com")));
        when(readerProfileService.resolveReaderId(any(), any())).thenReturn("reader-cookie-1");
        when(accountClaimSyncService.claimAndSync(eq("user-1"), eq("reader-cookie-1"), any()))
                .thenReturn(new AccountClaimSyncService.ClaimSyncResult(
                        true,
                        new AccountStateSnapshot(
                                java.util.List.of("book-1"),
                                java.util.Map.of(),
                                null,
                                java.util.Map.of("book-1", true)
                        )
                ));

        mockMvc.perform(post("/api/account/claim-sync")
                        .contentType("application/json")
                        .content("""
                                {"state":{"favoriteBookIds":["book-1"],"recapOptOut":{"book-1":true}}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimApplied", is(true)))
                .andExpect(jsonPath("$.state.favoriteBookIds[0]", is("book-1")))
                .andExpect(jsonPath("$.state.recapOptOut.book-1", is(true)));

        verify(accountAuthAuditService).record(eq("claim_sync"), eq("success_claim_applied"), any(), eq("reader@example.com"), eq("user-1"), isNull(), isNull());
    }

    private void allowRegisterAndLoginRateLimits() {
        when(accountAuthRateLimiter.checkRegister(any(), any())).thenReturn(AccountAuthRateLimiter.RateLimitResult.permitted());
        when(accountAuthRateLimiter.checkLogin(any(), any())).thenReturn(AccountAuthRateLimiter.RateLimitResult.permitted());
    }
}
