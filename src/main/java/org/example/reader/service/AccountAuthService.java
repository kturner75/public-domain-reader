package org.example.reader.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.reader.entity.UserEntity;
import org.example.reader.entity.UserSessionEntity;
import org.example.reader.repository.UserRepository;
import org.example.reader.repository.UserSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Service
public class AccountAuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final boolean enabled;
    private final String cookieName;
    private final int sessionTtlMinutes;
    private final boolean secureCookie;
    private final int minPasswordLength;
    private final int bcryptStrength;
    private final SecureRandom secureRandom = new SecureRandom();
    private final AtomicInteger cleanupTicker = new AtomicInteger();

    public AccountAuthService(
            UserRepository userRepository,
            UserSessionRepository userSessionRepository,
            @Value("${account.auth.enabled:false}") boolean enabled,
            @Value("${account.auth.cookie-name:pdr_account_session}") String cookieName,
            @Value("${account.auth.session.ttl-minutes:43200}") int sessionTtlMinutes,
            @Value("${account.auth.secure-cookie:false}") boolean secureCookie,
            @Value("${account.auth.password.min-length:10}") int minPasswordLength,
            @Value("${account.auth.password.bcrypt-strength:12}") int bcryptStrength) {
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
        this.enabled = enabled;
        this.cookieName = (cookieName == null || cookieName.isBlank()) ? "pdr_account_session" : cookieName;
        this.sessionTtlMinutes = Math.max(15, sessionTtlMinutes);
        this.secureCookie = secureCookie;
        this.minPasswordLength = Math.max(8, minPasswordLength);
        this.bcryptStrength = Math.min(14, Math.max(10, bcryptStrength));
    }

    @Transactional
    public AuthResult register(String email, String password, HttpServletResponse response) {
        if (!enabled) {
            return disabledResult();
        }

        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return AuthResult.error(ResultStatus.INVALID_EMAIL, enabled, "A valid email address is required.");
        }
        if (!isValidPassword(password)) {
            return AuthResult.error(
                    ResultStatus.INVALID_PASSWORD,
                    enabled,
                    "Password must be at least " + minPasswordLength + " characters.");
        }

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            return AuthResult.error(ResultStatus.EMAIL_ALREADY_EXISTS, enabled, "Email is already registered.");
        }

        UserEntity user = new UserEntity();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt(bcryptStrength)));

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            return AuthResult.error(ResultStatus.EMAIL_ALREADY_EXISTS, enabled, "Email is already registered.");
        }

        createSession(user, response);
        cleanupIfNeeded();
        return AuthResult.success(enabled, user.getEmail(), "Account created.");
    }

    @Transactional
    public AuthResult login(String email, String password, HttpServletResponse response) {
        if (!enabled) {
            return disabledResult();
        }

        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null || password == null || password.isBlank()) {
            return AuthResult.error(ResultStatus.INVALID_CREDENTIALS, enabled, "Invalid email or password.");
        }

        Optional<UserEntity> userOptional = userRepository.findByEmail(normalizedEmail);
        if (userOptional.isEmpty()) {
            return AuthResult.error(ResultStatus.INVALID_CREDENTIALS, enabled, "Invalid email or password.");
        }

        UserEntity user = userOptional.get();
        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            return AuthResult.error(ResultStatus.INVALID_CREDENTIALS, enabled, "Invalid email or password.");
        }

        createSession(user, response);
        cleanupIfNeeded();
        return AuthResult.success(enabled, user.getEmail(), "Signed in.");
    }

    @Transactional
    public AuthResult logout(HttpServletRequest request, HttpServletResponse response) {
        if (!enabled) {
            writeSessionCookie(response, "", 0);
            return new AuthResult(ResultStatus.DISABLED, false, false, null, "Account auth is disabled.");
        }

        String token = readToken(request);
        if (token != null && !token.isBlank()) {
            userSessionRepository.deleteByTokenHash(hashToken(token));
        }
        writeSessionCookie(response, "", 0);
        cleanupIfNeeded();
        return new AuthResult(ResultStatus.SUCCESS, true, false, null, "Signed out.");
    }

    @Transactional
    public AuthResult status(HttpServletRequest request) {
        if (!enabled) {
            return new AuthResult(ResultStatus.DISABLED, false, false, null, "Account auth is disabled.");
        }

        Optional<AccountPrincipal> principal = resolveAuthenticatedPrincipalInternal(request);
        if (principal.isEmpty()) {
            cleanupIfNeeded();
            return new AuthResult(ResultStatus.SUCCESS, true, false, null, null);
        }
        cleanupIfNeeded();
        return AuthResult.success(true, principal.get().email(), null);
    }

    @Transactional
    public Optional<AccountPrincipal> resolveAuthenticatedPrincipal(HttpServletRequest request) {
        if (!enabled) {
            return Optional.empty();
        }
        return resolveAuthenticatedPrincipalInternal(request);
    }

    private Optional<AccountPrincipal> resolveAuthenticatedPrincipalInternal(HttpServletRequest request) {
        String token = readToken(request);
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        String tokenHash = hashToken(token);
        Optional<UserSessionEntity> sessionOptional = userSessionRepository.findByTokenHash(tokenHash);
        if (sessionOptional.isEmpty()) {
            return Optional.empty();
        }

        UserSessionEntity session = sessionOptional.get();
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            userSessionRepository.deleteByTokenHash(tokenHash);
            return Optional.empty();
        }

        UserEntity user = session.getUser();
        return Optional.of(new AccountPrincipal(user.getId(), user.getEmail()));
    }

    private AuthResult disabledResult() {
        return new AuthResult(ResultStatus.DISABLED, false, false, null, "Account auth is disabled.");
    }

    private void createSession(UserEntity user, HttpServletResponse response) {
        String token = newToken();
        String tokenHash = hashToken(token);
        LocalDateTime now = LocalDateTime.now();

        UserSessionEntity session = new UserSessionEntity();
        session.setUser(user);
        session.setTokenHash(tokenHash);
        session.setCreatedAt(now);
        session.setLastAccessedAt(now);
        session.setExpiresAt(now.plusMinutes(sessionTtlMinutes));
        userSessionRepository.save(session);

        int maxAgeSeconds = Math.toIntExact(Duration.ofMinutes(sessionTtlMinutes).getSeconds());
        writeSessionCookie(response, token, maxAgeSeconds);
    }

    private void writeSessionCookie(HttpServletResponse response, String value, int maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String readToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean isValidPassword(String password) {
        return password != null && password.length() >= minPasswordLength;
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 320) {
            return null;
        }
        return EMAIL_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private String newToken() {
        byte[] raw = new byte[32];
        secureRandom.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            return Integer.toHexString(token.hashCode());
        }
    }

    private void cleanupIfNeeded() {
        int tick = cleanupTicker.incrementAndGet();
        if ((tick & 0xFF) != 0) {
            return;
        }
        userSessionRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }

    public enum ResultStatus {
        SUCCESS,
        DISABLED,
        INVALID_EMAIL,
        INVALID_PASSWORD,
        INVALID_CREDENTIALS,
        EMAIL_ALREADY_EXISTS
    }

    public record AuthResult(
            ResultStatus status,
            boolean accountAuthEnabled,
            boolean authenticated,
            String email,
            String message
    ) {
        public static AuthResult success(boolean accountAuthEnabled, String email, String message) {
            return new AuthResult(ResultStatus.SUCCESS, accountAuthEnabled, true, email, message);
        }

        public static AuthResult error(ResultStatus status, boolean accountAuthEnabled, String message) {
            return new AuthResult(status, accountAuthEnabled, false, null, message);
        }
    }

    public record AccountPrincipal(String userId, String email) {
    }
}
