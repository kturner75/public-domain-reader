package org.example.reader.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.reader.model.AccountStateSnapshot;
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

    public AccountController(
            AccountAuthService accountAuthService,
            ReaderProfileService readerProfileService,
            AccountClaimSyncService accountClaimSyncService,
            AccountMetricsService accountMetricsService) {
        this.accountAuthService = accountAuthService;
        this.readerProfileService = readerProfileService;
        this.accountClaimSyncService = accountClaimSyncService;
        this.accountMetricsService = accountMetricsService;
    }

    @GetMapping("/status")
    public AccountStatusResponse status(HttpServletRequest request) {
        accountMetricsService.recordStatusRead();
        return toResponse(accountAuthService.status(request));
    }

    @PostMapping("/register")
    public ResponseEntity<AccountStatusResponse> register(
            @RequestBody CredentialsRequest request,
            HttpServletResponse response) {
        if (request == null || isBlank(request.email()) || isBlank(request.password())) {
            return ResponseEntity.badRequest()
                    .body(buildStatusResponse(false, null, "Email and password are required."));
        }
        AccountAuthService.AuthResult result = accountAuthService.register(request.email(), request.password(), response);
        accountMetricsService.recordRegisterResult(result.status());
        return toEntity(result);
    }

    @PostMapping("/login")
    public ResponseEntity<AccountStatusResponse> login(
            @RequestBody CredentialsRequest request,
            HttpServletResponse response) {
        if (request == null || isBlank(request.email()) || isBlank(request.password())) {
            return ResponseEntity.badRequest()
                    .body(buildStatusResponse(false, null, "Email and password are required."));
        }
        AccountAuthService.AuthResult result = accountAuthService.login(request.email(), request.password(), response);
        accountMetricsService.recordLoginResult(result.status());
        return toEntity(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<AccountStatusResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        AccountAuthService.AuthResult result = accountAuthService.logout(request, response);
        accountMetricsService.recordLogoutResult(result.status());
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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String userId = principal.get().userId();
        String readerId = readerProfileService.resolveReaderId(httpRequest, httpResponse);
        AccountStateSnapshot incoming = request == null ? AccountStateSnapshot.empty() : request.state();
        try {
            AccountClaimSyncService.ClaimSyncResult result =
                    accountClaimSyncService.claimAndSync(userId, readerId, incoming);
            accountMetricsService.recordClaimSyncSuccess(result.claimApplied());
            return ResponseEntity.ok(new ClaimSyncResponse(result.claimApplied(), result.state()));
        } catch (RuntimeException ex) {
            accountMetricsService.recordClaimSyncFailure();
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
            case EMAIL_ALREADY_EXISTS -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(toResponse(result));
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
