package app.strata.auth;

import app.strata.auth.dto.AuthDtos.AuthResponse;
import app.strata.auth.dto.AuthDtos.ForgotPasswordRequest;
import app.strata.auth.dto.AuthDtos.IssuedSession;
import app.strata.auth.dto.AuthDtos.LoginRequest;
import app.strata.auth.dto.AuthDtos.RegisterRequest;
import app.strata.auth.dto.AuthDtos.ResetPasswordRequest;
import app.strata.auth.dto.AuthDtos.SessionDto;
import app.strata.auth.dto.AuthDtos.TokenRequest;
import app.strata.auth.dto.AuthDtos.UsernameAvailability;
import app.strata.common.config.AppProperties;
import app.strata.common.error.ApiException;
import app.strata.common.ratelimit.RateLimiter;
import app.strata.common.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    public static final String COOKIE = "strata_rt";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService auth;
    private final RateLimiter limiter;
    private final AppProperties props;

    public AuthController(AuthService auth, RateLimiter limiter, AppProperties props) {
        this.auth = auth;
        this.limiter = limiter;
        this.props = props;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http, HttpServletResponse res) {
        limiter.check("register:" + clientIp(http), 5, Duration.ofHours(1));
        return withCookie(auth.register(req, http.getHeader(HttpHeaders.USER_AGENT), clientIp(http)), res);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http, HttpServletResponse res) {
        limiter.check("login:ip:" + clientIp(http), 30, Duration.ofMinutes(10));
        limiter.check("login:acct:" + req.login().toLowerCase(), 10, Duration.ofMinutes(10));
        return withCookie(auth.login(req.login(), req.password(), http.getHeader(HttpHeaders.USER_AGENT), clientIp(http)), res);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@CookieValue(name = COOKIE, required = false) String token,
                                HttpServletRequest http, HttpServletResponse res) {
        requireAllowedOrigin(http);
        limiter.check("refresh:" + clientIp(http), 60, Duration.ofMinutes(1));
        try {
            return withCookie(auth.refresh(token, http.getHeader(HttpHeaders.USER_AGENT), clientIp(http)), res);
        } catch (ApiException e) {
            clearCookie(res);
            throw e;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = COOKIE, required = false) String token, HttpServletRequest http,
                       HttpServletResponse res) {
        requireAllowedOrigin(http);
        auth.logout(token);
        clearCookie(res);
    }

    @GetMapping("/username-available")
    public UsernameAvailability usernameAvailable(@RequestParam String username, HttpServletRequest http) {
        limiter.check("uname:" + clientIp(http), 120, Duration.ofMinutes(1));
        return auth.usernameAvailability(username);
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody TokenRequest req) {
        auth.verifyEmail(req.token());
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resend(@AuthenticationPrincipal AuthUser user) {
        limiter.check("resend:" + user.id(), 3, Duration.ofHours(1));
        auth.resendVerification(user.id());
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgot(@Valid @RequestBody ForgotPasswordRequest req, HttpServletRequest http) {
        limiter.check("forgot:" + clientIp(http), 5, Duration.ofHours(1));
        auth.forgotPassword(req.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetPasswordRequest req, HttpServletRequest http) {
        limiter.check("reset:" + clientIp(http), 10, Duration.ofHours(1));
        auth.resetPassword(req.token(), req.newPassword());
    }

    @GetMapping("/sessions")
    public List<SessionDto> sessions(@AuthenticationPrincipal AuthUser user,
                                     @CookieValue(name = COOKIE, required = false) String token) {
        return auth.sessions(user.id(), token);
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        auth.revokeSession(user.id(), id);
    }

    private AuthResponse withCookie(IssuedSession s, HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, s.refreshToken())
                .httpOnly(true).secure(props.cookie().secure()).sameSite("Strict").path(COOKIE_PATH)
                .maxAge(props.jwt().refreshTtl()).build().toString());
        return s.response();
    }

    private void clearCookie(HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, "")
                .httpOnly(true).secure(props.cookie().secure()).sameSite("Strict").path(COOKIE_PATH)
                .maxAge(0).build().toString());
    }

    /** Defence in depth for the cookie-authenticated endpoints, on top of SameSite=Strict. */
    private void requireAllowedOrigin(HttpServletRequest http) {
        String origin = http.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !props.cors().allowedOrigins().contains(origin)) {
            throw ApiException.forbidden("Origin not allowed");
        }
    }

    /** Behind the reverse proxy, server.forward-headers-strategy=native resolves X-Forwarded-For. */
    static String clientIp(HttpServletRequest http) {
        return http.getRemoteAddr();
    }
}
