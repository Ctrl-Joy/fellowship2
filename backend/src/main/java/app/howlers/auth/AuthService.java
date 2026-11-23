package app.howlers.auth;

import app.howlers.auth.dto.AuthDtos.AuthResponse;
import app.howlers.auth.dto.AuthDtos.IssuedSession;
import app.howlers.auth.dto.AuthDtos.RegisterRequest;
import app.howlers.auth.dto.AuthDtos.SessionDto;
import app.howlers.auth.dto.AuthDtos.UsernameAvailability;
import app.howlers.auth.dto.AuthDtos.VerifyEmailResult;
import app.howlers.common.config.AppProperties;
import app.howlers.common.error.ApiException;
import app.howlers.common.security.JwtService;
import app.howlers.common.text.Tokens;
import app.howlers.user.User;
import app.howlers.user.UserMapper;
import app.howlers.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Set<String> RESERVED = Set.of("admin", "administrator", "root", "support", "howlers",
            "moderator", "mod", "system", "api", "me", "null", "undefined", "deleted", "settings", "help");

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final EmailTokenRepository emailTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final MailService mail;
    static final Duration VERIFY_TTL = Duration.ofHours(24);
    static final Duration RESET_TTL = Duration.ofHours(1);
    private final Duration refreshTtl;
    /** Real bcrypt hash used to equalise login timing when the account does not exist. */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, EmailTokenRepository emailTokens,
                       PasswordEncoder encoder, JwtService jwt, MailService mail, AppProperties props) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.emailTokens = emailTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.mail = mail;
        this.refreshTtl = props.jwt().refreshTtl();
        this.dummyHash = encoder.encode(Tokens.random(16));
    }

    public UsernameAvailability usernameAvailability(String username) {
        if (username == null || !username.matches("^[A-Za-z0-9_]{3,21}$")) {
            return new UsernameAvailability(false, "Use 3-21 letters, digits or underscores");
        }
        if (RESERVED.contains(username.toLowerCase())) return new UsernameAvailability(false, "That name is reserved");
        if (users.existsByUsernameIgnoreCase(username)) return new UsernameAvailability(false, "That name is taken");
        return new UsernameAvailability(true, null);
    }

    @Transactional
    public IssuedSession register(RegisterRequest req, String userAgent, String ip) {
        UsernameAvailability a = usernameAvailability(req.username());
        if (!a.available()) throw ApiException.conflict("username", a.reason());
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("email", "An account with that email already exists");
        }
        String display = req.displayName() == null || req.displayName().isBlank() ? req.username() : req.displayName().trim();
        User user;
        try {
            user = users.saveAndFlush(new User(req.username(), req.email().trim().toLowerCase(), encoder.encode(req.password()), display));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("username", "That name or email is already registered");
        }
        sendVerification(user);
        return issue(user, UUID.randomUUID(), userAgent, ip);
    }

    @Transactional
    public IssuedSession login(String login, String password, String userAgent, String ip) {
        User user = (login.contains("@") ? users.findByEmailIgnoreCase(login.trim()) : users.findByUsernameIgnoreCase(login.trim()))
                .orElse(null);
        if (user == null) {
            encoder.matches(password, dummyHash);
            throw ApiException.unauthorized("Incorrect username or password");
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("Incorrect username or password");
        }
        if (User.Status.DELETED.name().equals(user.getStatus())) {
            throw ApiException.unauthorized("Incorrect username or password");
        }
        if (User.Status.SUSPENDED.name().equals(user.getStatus())) {
            throw ApiException.forbidden("This account is suspended");
        }
        if (User.Status.DEACTIVATED.name().equals(user.getStatus())) {
            user.setStatus(User.Status.ACTIVE); // logging in reactivates
        }
        return issue(user, UUID.randomUUID(), userAgent, ip);
    }

    /**
     * Rotation with reuse detection: a token can be exchanged exactly once. Presenting an already-rotated
     * token means it leaked (or a race lost); the whole family is revoked and the user must sign in again.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public IssuedSession refresh(String rawToken, String userAgent, String ip) {
        if (rawToken == null || rawToken.isBlank()) throw ApiException.unauthorized("No session");
        Instant now = Instant.now();
        RefreshToken current = refreshTokens.findForUpdate(Tokens.sha256(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("Session expired"));
        if (current.getRotatedAt() != null && current.getRevokedAt() == null) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user {} family {}", current.getUserId(), current.getFamilyId());
            throw ApiException.unauthorized("Session expired");
        }
        if (!current.isUsable(now)) throw ApiException.unauthorized("Session expired");
        User user = users.findById(current.getUserId()).filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("Session expired"));
        current.markRotated(now);
        return issue(user, current.getFamilyId(), userAgent, ip);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokens.findByTokenHash(Tokens.sha256(rawToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<SessionDto> sessions(long userId, String currentRawToken) {
        String currentHash = currentRawToken == null ? null : Tokens.sha256(currentRawToken);
        return refreshTokens.findActiveSessions(userId, Instant.now()).stream()
                .map(t -> new SessionDto(t.getId(), t.getUserAgent(), t.getIp(), t.getCreatedAt(), t.getLastUsedAt(),
                        t.getTokenHash().equals(currentHash)))
                .toList();
    }

    @Transactional
    public void revokeSession(long userId, long sessionId) {
        RefreshToken t = refreshTokens.findById(sessionId).filter(r -> r.getUserId() == userId)
                .orElseThrow(() -> ApiException.notFound("Session"));
        refreshTokens.revokeFamily(t.getFamilyId(), Instant.now());
    }

    /**
     * Distinguishes the states the verify page shows: confirmed now, confirmed earlier (any of the user's verify links,
     * used or not), expired or replaced by a newer link ({@code LINK_EXPIRED}), and unknown ({@code LINK_INVALID}).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public VerifyEmailResult verifyEmail(String rawToken) {
        EmailToken t = emailTokens.findByTokenHash(Tokens.sha256(rawToken))
                .filter(x -> x.getPurpose().equals(EmailToken.Purpose.VERIFY_EMAIL.name()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "LINK_INVALID",
                        "This verification link isn't valid. Request a new one from Settings."));
        User u = users.findById(t.getUserId()).filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "LINK_INVALID",
                        "This verification link isn't valid."));
        if (u.isEmailVerified()) {
            if (t.getUsedAt() == null) t.markUsed();
            return new VerifyEmailResult("ALREADY_VERIFIED");
        }
        if (t.getUsedAt() != null || !t.getExpiresAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LINK_EXPIRED",
                    "This verification link has expired or was replaced by a newer one.");
        }
        t.markUsed();
        u.setEmailVerified(true);
        return new VerifyEmailResult("VERIFIED");
    }

    @Transactional
    public void resendVerification(long userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (u.isEmailVerified()) throw ApiException.badRequest("Email already verified");
        sendVerification(u);
    }

    /** Always succeeds from the caller's perspective to avoid account enumeration. */
    @Transactional
    public void forgotPassword(String email) {
        users.findByEmailIgnoreCase(email.trim()).filter(u -> u.getDeletedAt() == null).ifPresent(u -> {
            emailTokens.invalidateOutstanding(u.getId(), EmailToken.Purpose.RESET_PASSWORD.name());
            String token = Tokens.random(32);
            emailTokens.save(new EmailToken(u.getId(), EmailToken.Purpose.RESET_PASSWORD, Tokens.sha256(token),
                    Instant.now().plus(RESET_TTL)));
            mail.sendPasswordReset(u.getEmail(), u.getUsername(), token, RESET_TTL);
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        EmailToken t = consume(rawToken, EmailToken.Purpose.RESET_PASSWORD);
        User u = users.findById(t.getUserId()).orElseThrow(() -> ApiException.badRequest("Invalid or expired link"));
        u.setPasswordHash(encoder.encode(newPassword));
        refreshTokens.revokeAllForUser(u.getId(), Instant.now());
    }

    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        int n = refreshTokens.deleteExpired(Instant.now().minus(Duration.ofDays(7)));
        if (n > 0) log.info("Purged {} expired refresh tokens", n);
    }

    private EmailToken consume(String rawToken, EmailToken.Purpose purpose) {
        EmailToken t = emailTokens.findByTokenHash(Tokens.sha256(rawToken))
                .filter(x -> x.getPurpose().equals(purpose.name()))
                .filter(x -> x.getUsedAt() == null && x.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired link"));
        t.markUsed();
        return t;
    }

    private void sendVerification(User u) {
        emailTokens.invalidateOutstanding(u.getId(), EmailToken.Purpose.VERIFY_EMAIL.name());
        String token = Tokens.random(32);
        emailTokens.save(new EmailToken(u.getId(), EmailToken.Purpose.VERIFY_EMAIL, Tokens.sha256(token),
                Instant.now().plus(VERIFY_TTL)));
        mail.sendVerification(u.getEmail(), u.getUsername(), token, VERIFY_TTL);
    }

    private IssuedSession issue(User user, UUID family, String userAgent, String ip) {
        String raw = Tokens.random(48);
        refreshTokens.save(new RefreshToken(user.getId(), family, Tokens.sha256(raw), Instant.now().plus(refreshTtl),
                truncate(userAgent, 300), truncate(ip, 64)));
        String access = jwt.issueAccessToken(user.getId(), user.getUsername(), user.getRole());
        return new IssuedSession(new AuthResponse(access, jwt.accessTtlSeconds(), UserMapper.toMe(user)), raw);
    }

    private static String truncate(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }
}
