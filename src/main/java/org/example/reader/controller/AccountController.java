package org.example.reader.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.reader.model.AccountStateSnapshot;
import org.example.reader.service.AccountAuthService;
import org.example.reader.service.AccountClaimSyncService;
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

    public AccountController(
            AccountAuthService accountAuthService,
            ReaderProfileService readerProfileService,
            AccountClaimSyncService accountClaimSyncService) {
        this.accountAuthService = accountAuthService;
        this.readerProfileService = readerProfileService;
        this.accountClaimSyncService = accountClaimSyncService;
    }

    @GetMapping("/status")
    public AccountStatusResponse status(HttpServletRequest request) {
        return toResponse(accountAuthService.status(request));
    }

    @PostMapping("/register")
    public ResponseEntity<AccountStatusResponse> register(
            @RequestBody CredentialsRequest request,
            HttpServletResponse response) {
        if (request == null || isBlank(request.email()) || isBlank(request.password())) {
            return ResponseEntity.badRequest()
                    .body(new AccountStatusResponse(false, false, null, "Email and password are required."));
        }
        AccountAuthService.AuthResult result = accountAuthService.register(request.email(), request.password(), response);
        return toEntity(result);
    }

    @PostMapping("/login")
    public ResponseEntity<AccountStatusResponse> login(
            @RequestBody CredentialsRequest request,
            HttpServletResponse response) {
        if (request == null || isBlank(request.email()) || isBlank(request.password())) {
            return ResponseEntity.badRequest()
                    .body(new AccountStatusResponse(false, false, null, "Email and password are required."));
        }
        AccountAuthService.AuthResult result = accountAuthService.login(request.email(), request.password(), response);
        return toEntity(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<AccountStatusResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(toResponse(accountAuthService.logout(request, response)));
    }

    @PostMapping("/claim-sync")
    public ResponseEntity<ClaimSyncResponse> claimSync(
            @RequestBody(required = false) ClaimSyncRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        var principal = accountAuthService.resolveAuthenticatedPrincipal(httpRequest);
        if (principal.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String userId = principal.get().userId();
        String readerId = readerProfileService.resolveReaderId(httpRequest, httpResponse);
        AccountStateSnapshot incoming = request == null ? AccountStateSnapshot.empty() : request.state();
        AccountClaimSyncService.ClaimSyncResult result =
                accountClaimSyncService.claimAndSync(userId, readerId, incoming);
        return ResponseEntity.ok(new ClaimSyncResponse(result.claimApplied(), result.state()));
    }

    private ResponseEntity<AccountStatusResponse> toEntity(AccountAuthService.AuthResult result) {
        HttpStatus status = switch (result.status()) {
            case SUCCESS -> HttpStatus.OK;
            case DISABLED -> HttpStatus.SERVICE_UNAVAILABLE;
            case INVALID_EMAIL, INVALID_PASSWORD -> HttpStatus.BAD_REQUEST;
            case INVALID_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case EMAIL_ALREADY_EXISTS -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(toResponse(result));
    }

    private AccountStatusResponse toResponse(AccountAuthService.AuthResult result) {
        return new AccountStatusResponse(
                result.accountAuthEnabled(),
                result.authenticated(),
                result.email(),
                result.message()
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
            String message
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
