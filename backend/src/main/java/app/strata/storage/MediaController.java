package app.strata.storage;

import app.strata.common.ratelimit.RateLimiter;
import app.strata.common.security.AuthUser;
import app.strata.storage.MediaService.MediaDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Media")
@RestController
public class MediaController {
    private final MediaService media;
    private final StorageService storage;
    private final RateLimiter limiter;

    public MediaController(MediaService media, StorageService storage, RateLimiter limiter) {
        this.media = media;
        this.storage = storage;
        this.limiter = limiter;
    }

    @PostMapping(value = "/api/v1/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MediaDto upload(@AuthenticationPrincipal AuthUser user, @RequestParam("file") MultipartFile file) {
        limiter.check("media:" + user.id(), 30, Duration.ofHours(1));
        return media.uploadPostImage(user.id(), file);
    }

    /** Public, immutable media. Keys are random, so URLs are unguessable but not access-controlled. */
    @GetMapping("/api/v1/files/**")
    public ResponseEntity<InputStreamResource> file(HttpServletRequest req) {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        String key = path.substring("/api/v1/files/".length());
        return storage.get(key)
                .map(o -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(o.contentType()))
                        .contentLength(o.size())
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .header("X-Content-Type-Options", "nosniff")
                        .header("Content-Security-Policy", "default-src 'none'")
                        .body(new InputStreamResource(o.stream())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
