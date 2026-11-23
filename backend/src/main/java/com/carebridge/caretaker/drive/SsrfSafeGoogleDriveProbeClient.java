package com.carebridge.caretaker.drive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * SSRF-safe Google Drive reachability probe that disables automatic redirects,
 * verifies DNS resolution against private/internal IP ranges before each hop,
 * and flags sign-in redirects as private/restricted links for Admin review.
 */
@Component
public class SsrfSafeGoogleDriveProbeClient implements DriveLinkProbeClient {

    private static final Logger log = LoggerFactory.getLogger(SsrfSafeGoogleDriveProbeClient.class);
    private static final int MAX_REDIRECTS = 3;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;

    public SsrfSafeGoogleDriveProbeClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public DriveProbeResult probe(String normalizedDriveUrl) {
        try {
            URI currentUri = GoogleDriveUrlValidator.parseStrictUri(normalizedDriveUrl);
            GoogleDriveUrlValidator.validateAllowedDriveUri(currentUri);

            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                verifySafePublicHost(currentUri);

                HttpResponse<Void> response = sendHeadWithGetFallback(currentUri);
                int status = response.statusCode();

                if (status >= 200 && status < 300) {
                    return new DriveProbeResult(true, "Publicly reachable (HTTP " + status + ").");
                }

                if (status == 401 || status == 403) {
                    return new DriveProbeResult(
                            false,
                            "Link is private or restricted (HTTP " + status + "). Enable 'Anyone with the link can view'."
                    );
                }

                if (status == 404) {
                    return new DriveProbeResult(false, "Document not found (HTTP 404).");
                }

                if (status >= 300 && status < 400) {
                    String location = response.headers().firstValue("location").orElse(null);
                    if (location == null || location.isBlank()) {
                        return new DriveProbeResult(false, "Incomplete redirect response from Google Drive.");
                    }

                    URI nextUri = currentUri.resolve(location);
                    if (GoogleDriveUrlValidator.isGoogleSignInHost(nextUri.getHost())) {
                        return new DriveProbeResult(
                                false,
                                "Link requires Google sign-in. Share as 'Anyone with the link can view'."
                        );
                    }
                    if (!"https".equalsIgnoreCase(nextUri.getScheme())
                            || !GoogleDriveUrlValidator.isAllowedRedirectHost(nextUri.getHost())) {
                        return new DriveProbeResult(false, "Blocked unexpected redirect target.");
                    }
                    currentUri = nextUri;
                    continue;
                }

                return new DriveProbeResult(false, "Link returned unexpected status HTTP " + status + ".");
            }

            return new DriveProbeResult(false, "Too many redirects while checking Google Drive link.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new DriveProbeResult(false, "Link check was interrupted.");
        } catch (Exception ex) {
            log.debug("Google Drive probe failed for {}: {}", normalizedDriveUrl, ex.getMessage());
            return new DriveProbeResult(false, "Could not reach Google Drive link within timeout.");
        }
    }

    private HttpResponse<Void> sendHeadWithGetFallback(URI uri) throws IOException, InterruptedException {
        HttpRequest headRequest = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("User-Agent", "CareBridge-LinkVerifier/1.0")
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<Void> headResponse = httpClient.send(headRequest, HttpResponse.BodyHandlers.discarding());
        if (headResponse.statusCode() != 405 && headResponse.statusCode() != 501) {
            return headResponse;
        }

        HttpRequest getRequest = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("User-Agent", "CareBridge-LinkVerifier/1.0")
                .GET()
                .build();
        return httpClient.send(getRequest, HttpResponse.BodyHandlers.discarding());
    }

    private void verifySafePublicHost(URI uri) throws IOException {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IOException("Missing target host.");
        }
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) {
            throw new IOException("Could not resolve host.");
        }
        for (InetAddress address : addresses) {
            if (GoogleDriveUrlValidator.isPrivateOrReservedAddress(address)) {
                throw new IOException("Blocked private or reserved destination IP.");
            }
        }
    }
}
