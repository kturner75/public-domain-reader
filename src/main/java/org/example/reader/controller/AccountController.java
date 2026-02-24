package org.example.reader.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.reader.model.AccountStateSnapshot;
import org.example.reader.service.AccountAuthAuditService;
import org.example.reader.service.AccountAuthRateLimiter;
import org.example.reader.service.AccountAuthService;
import org.example.reader.service.AccountClaimSyncService;
import org.example.reader.service.AccountMetricsService;
import org.example.reader.service.ReaderProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountAuthService accountAuthService;
    private final ReaderProfileService readerProfileService;
    private final AccountClaimSyncService accountClaimSyncService;
    private final AccountMetricsService accountMetricsService;
    private final AccountAuthRateLimiter accountAuthRateLimiter;
    private final AccountAuthAuditService accountAuthAuditService;

    public AccountController(
            AccountAuthService accountAuthService,
            ReaderProfileService readerProfileService,
            AccountClaimSyncService accountClaimSyncService,
            AccountMetricsService accountMetricsService,
            AccountAuthRateLimiter accountAuthRateLimiter,
            AccountAuthAuditService accountAuthAuditService) {
        this.accountAuthService = accountAuthService;
        this.readerProfileService = readerProfileService;
        this.accountClaimSyncService = accountClaimSyncService;
        this.accountMetricsService = accountMetricsService;
        this.accountAuthRateLimiter = accountAuthRateLimiter;
        this.accountAuthAuditService = accountAuthAuditService;
    }

    @GetMapping("/status")
    public AccountStatusResponse status(HttpServletRequest request) {
        accountMetricsService.recordStatusRead();
        return toResponse(accountAuthService.status(request));
    }

    @PostMapping("/register")
    public ResponseEntity<AccountStatusResponse> register(
            @RequestBody CredentialsRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        if (request == null || isBlank(request.email()) || isBlank(request.password())) {
            accountAuthAuditService.record("register", "invalid_request", httpRequest, null, null, null, null);
            return ResponseEntity.badRequest()
                    .body(buildStatusResponse(false, null, "Email and password are required."));
        }

        AccountAuthRateLimiter.RateLimitResult rateLimit = accountAuthRateLimiter.checkRegister(httpRequest, request.email());
        if (!rateLimit.allowed()) {
            accountMetricsService.recordRegisterRateLimited();
            accountAuthAuditService.record(
                    "register",
                    "rate_limited",
                    httpRequest,
                    request.email(),
                    null,
                    rateLimit.retryAfterSeconds(),
                    rateLimit.scope()
            );
            return rateLimitedResponse(rateLimit.retryAfterSeconds());
        }

        AccountAuthService.AuthResult result = accountAuthService.register(request.email(), request.password(), response);
        accountMetricsService.recordRegisterResult(result.status());
        accountAuthAuditService.record(
                "register",
                outcomeFor(result.status()),
                httpRequest,
                request.email(),
                null,
                result.retryAfterSeconds(),
                null
        );
        return toEntity(result);
    }

    @PostMapping("/login")
    public ResponseEntity<AccountStatusResponse> login(
            @RequestBody CredentialsRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        if (request == null || isBlank(request.email()) || isBlank(request.password())) {
            accountAuthAuditService.record("login", "invalid_request", httpRequest, null, null, null, null);
            return ResponseEntity.badRequest()
                    .body(buildStatusResponse(false, null, "Email and password are required."));
        }

        AccountAuthRateLimiter.RateLimitResult rateLimit = accountAuthRateLimiter.checkLogin(httpRequest, request.email());
        if (!rateLimit.allowed()) {
            accountMetricsService.recordLoginRateLimited();
            accountAuthAuditService.record(
                    "login",
                    "rate_limited",
                    httpRequest,
                    request.email(),
                    null,
                    rateLimit.retryAfterSeconds(),
                    rateLimit.scope()
            );
            return rateLimitedResponse(rateLimit.retryAfterSeconds());
        }

        AccountAuthService.AuthResult result = accountAuthService.login(request.email(), request.password(), response);
        accountMetricsService.recordLoginResult(result.status());
        accountAuthAuditService.record(
                "login",
                outcomeFor(result.status()),
                httpRequest,
                request.email(),
                null,
                result.retryAfterSeconds(),
                null
        );
        return toEntity(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<AccountStatusResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        var principal = accountAuthService.resolveAuthenticatedPrincipal(request);
        AccountAuthService.AuthResult result = accountAuthService.logout(request, response);
        accountMetricsService.recordLogoutResult(result.status());
        accountAuthAuditService.record(
                "logout",
                outcomeFor(result.status()),
                request,
                principal.map(AccountAuthService.AccountPrincipal::email).orElse(null),
                principal.map(AccountAuthService.AccountPrincipal::userId).orElse(null),
                result.retryAfterSeconds(),
                null
        );
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping("/claim-sync")
    public ResponseEntity<ClaimSyncResponse> claimSync(
            @RequestBody(required = false) ClaimSyncRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        var principal = accountAuthService.resolveAuthenticatedPrincipal(httpRequest);
        if (principal.isEmpty()) {
            accountMetricsService.recordClaimSyncUnauthorized();
            accountAuthAuditService.record("claim_sync", "unauthorized", httpRequest, null, null, null, null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String userId = principal.get().userId();
        String email = principal.get().email();
        String readerId = readerProfileService.resolveReaderId(httpRequest, httpResponse);
        AccountStateSnapshot incoming = request == null ? AccountStateSnapshot.empty() : request.state();
        try {
            AccountClaimSyncService.ClaimSyncResult result =
                    accountClaimSyncService.claimAndSync(userId, readerId, incoming);
            accountMetricsService.recordClaimSyncSuccess(result.claimApplied());
            accountAuthAuditService.record(
                    "claim_sync",
                    result.claimApplied() ? "success_claim_applied" : "success_noop",
                    httpRequest,
                    email,
                    userId,
                    null,
                    null
            );
            return ResponseEntity.ok(new ClaimSyncResponse(result.claimApplied(), result.state()));
        } catch (RuntimeException ex) {
            accountMetricsService.recordClaimSyncFailure();
            accountAuthAuditService.record("claim_sync", "failure", httpRequest, email, userId, null, null);
            throw ex;
        }
    }

    private ResponseEntity<AccountStatusResponse> toEntity(AccountAuthService.AuthResult result) {
        HttpStatus status = switch (result.status()) {
            case SUCCESS -> HttpStatus.OK;
            case DISABLED -> HttpStatus.SERVICE_UNAVAILABLE;
            case ROLLOUT_RESTRICTED -> HttpStatus.FORBIDDEN;
            case INVALID_EMAIL, INVALID_PASSWORD -> HttpStatus.BAD_REQUEST;
            case INVALID_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case ACCOUNT_LOCKED -> HttpStatus.TOO_MANY_REQUESTS;
            case EMAIL_ALREADY_EXISTS -> HttpStatus.CONFLICT;
        };
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (status == HttpStatus.TOO_MANY_REQUESTS && result.retryAfterSeconds() != null && result.retryAfterSeconds() > 0) {
            builder.header("Retry-After", String.valueOf(result.retryAfterSeconds()));
        }
        return builder.body(toResponse(result));
    }

    private ResponseEntity<AccountStatusResponse> rateLimitedResponse(Integer retryAfterSeconds) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            builder.header("Retry-After", String.valueOf(retryAfterSeconds));
        }
        return builder.body(buildStatusResponse(false, null, "Too many account auth attempts. Please retry shortly."));
    }

    private String outcomeFor(AccountAuthService.ResultStatus status) {
        return switch (status) {
            case SUCCESS -> "success";
            case DISABLED -> "disabled";
            case ROLLOUT_RESTRICTED -> "rollout_restricted";
            case INVALID_EMAIL -> "invalid_email";
            case INVALID_PASSWORD -> "invalid_password";
            case INVALID_CREDENTIALS -> "invalid_credentials";
            case ACCOUNT_LOCKED -> "account_locked";
            case EMAIL_ALREADY_EXISTS -> "email_exists";
        };
    }

    private AccountStatusResponse toResponse(AccountAuthService.AuthResult result) {
        return buildStatusResponse(
                result.accountAuthEnabled(),
                result.authenticated(),
                result.email(),
                result.message()
        );
    }

    private AccountStatusResponse buildStatusResponse(
            boolean accountAuthEnabled,
            boolean authenticated,
            String email,
            String message) {
        return new AccountStatusResponse(
                accountAuthEnabled,
                authenticated,
                email,
                message,
                accountAuthService.getRolloutMode(),
                accountAuthService.isAccountRequired()
        );
    }

    private AccountStatusResponse buildStatusResponse(boolean authenticated, String email, String message) {
        return buildStatusResponse(
                accountAuthService.isAccountAuthEnabled(),
                authenticated,
                email,
                message
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record CredentialsRequest(String email, String password) {
    }

    public record AccountStatusResponse(
            boolean accountAuthEnabled,
            boolean authenticated,
            String email,
            String message,
            String rolloutMode,
            boolean accountRequired
    ) {
    }

    public record ClaimSyncRequest(AccountStateSnapshot state) {
    }

    public record ClaimSyncResponse(
            boolean claimApplied,
            AccountStateSnapshot state
    ) {
    }
}
