package app.strata.common.security;

import app.strata.common.error.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates the security context from a Bearer token. A request that presents an invalid or expired
 * token is rejected with 401 (even on public endpoints) so the client knows to refresh, instead of
 * silently being treated as anonymous.
 */
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final ObjectMapper mapper;

    public JwtAuthFilter(JwtService jwt, ObjectMapper mapper) {
        this.jwt = jwt;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && !req.getRequestURI().startsWith("/api/v1/auth/")) {
            Optional<AuthUser> user = jwt.verify(header.substring(7));
            if (user.isEmpty()) {
                res.setStatus(HttpStatus.UNAUTHORIZED.value());
                res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                mapper.writeValue(res.getOutputStream(),
                        GlobalExceptionHandler.body(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Access token expired or invalid"));
                return;
            }
            var auth = new UsernamePasswordAuthenticationToken(user.get(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.get().role())));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(req, res);
    }
}
