package org.example.reader.service;

import jakarta.servlet.http.Cookie;
import org.example.reader.entity.UserEntity;
import org.example.reader.entity.UserSessionEntity;
import org.example.reader.repository.UserRepository;
import org.example.reader.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountAuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    private AccountAuthService accountAuthService;

    @BeforeEach
    void setUp() {
        accountAuthService = new AccountAuthService(
                userRepository,
                userSessionRepository,
                true,
                "pdr_account_session",
                60,
                false,
                10,
                10
        );
    }

    @Test
    void register_validCredentials_createsUserAndSessionCookie() {
        AtomicReference<UserSessionEntity> storedSession = new AtomicReference<>();
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId("user-1");
            return user;
        });
        when(userSessionRepository.save(any(UserSessionEntity.class))).thenAnswer(invocation -> {
            UserSessionEntity session = invocation.getArgument(0);
            storedSession.set(session);
            return session;
        });

        MockHttpServletResponse response = new MockHttpServletResponse();
        AccountAuthService.AuthResult result = accountAuthService.register(
                "Reader@Example.com",
                "password123",
                response
        );

        assertEquals(AccountAuthService.ResultStatus.SUCCESS, result.status());
        assertTrue(result.authenticated());
        assertEquals("reader@example.com", result.email());

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("reader@example.com", userCaptor.getValue().getEmail());
        assertTrue(BCrypt.checkpw("password123", userCaptor.getValue().getPasswordHash()));

        assertNotNull(storedSession.get());
        String setCookie = response.getHeader("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("pdr_account_session="));
    }

    @Test
    void status_withValidSessionCookie_returnsAuthenticated() {
        AtomicReference<UserSessionEntity> storedSession = new AtomicReference<>();
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId("user-1");
            return user;
        });
        when(userSessionRepository.save(any(UserSessionEntity.class))).thenAnswer(invocation -> {
            UserSessionEntity session = invocation.getArgument(0);
            storedSession.set(session);
            return session;
        });
        when(userSessionRepository.findByTokenHash(anyString())).thenAnswer(invocation -> {
            String requestedHash = invocation.getArgument(0);
            UserSessionEntity session = storedSession.get();
            if (session != null && session.getTokenHash().equals(requestedHash)) {
                return Optional.of(session);
            }
            return Optional.empty();
        });

        MockHttpServletResponse registerResponse = new MockHttpServletResponse();
        accountAuthService.register("reader@example.com", "password123", registerResponse);
        String token = extractCookieValue(registerResponse.getHeader("Set-Cookie"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("pdr_account_session", token));

        AccountAuthService.AuthResult status = accountAuthService.status(request);

        assertEquals(AccountAuthService.ResultStatus.SUCCESS, status.status());
        assertTrue(status.authenticated());
        assertEquals("reader@example.com", status.email());
    }

    @Test
    void login_unknownEmail_returnsInvalidCredentials() {
        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.empty());

        MockHttpServletResponse response = new MockHttpServletResponse();
        AccountAuthService.AuthResult result = accountAuthService.login(
                "reader@example.com",
                "wrong-password",
                response
        );

        assertEquals(AccountAuthService.ResultStatus.INVALID_CREDENTIALS, result.status());
        assertTrue(response.getHeaders("Set-Cookie").isEmpty());
    }

    @Test
    void logout_clearsCookieAndDeletesSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("pdr_account_session", "token-abc"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        AccountAuthService.AuthResult result = accountAuthService.logout(request, response);

        assertEquals(AccountAuthService.ResultStatus.SUCCESS, result.status());
        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"));
        verify(userSessionRepository).deleteByTokenHash(hash("token-abc"));
    }

    private String extractCookieValue(String setCookieHeader) {
        return setCookieHeader.split(";", 2)[0].split("=", 2)[1];
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
