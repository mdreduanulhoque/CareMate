package com.carebridge.caretaker.drive;

import com.carebridge.common.BadRequestException;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validates Google Drive document URLs strictly to enforce HTTPS, host allowlisting,
 * expected Drive/Docs resource paths, and SSRF protection against private/internal IPs.
 */
public final class GoogleDriveUrlValidator {

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "drive.google.com",
            "docs.google.com"
    );

    private static final List<String> ALLOWED_PATH_PREFIXES = List.of(
            "/file/d/",
            "/drive/folders/",
            "/drive/u/",
            "/open",
            "/uc",
            "/document/d/",
            "/spreadsheets/d/",
            "/presentation/d/",
            "/forms/d/"
    );

    private GoogleDriveUrlValidator() {
    }

    public static String validateAndNormalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new BadRequestException("Google Drive document link is required.");
        }

        String trimmed = rawUrl.trim();
        if (trimmed.length() > 1000) {
            throw new BadRequestException("Google Drive link is too long (maximum 1000 characters).");
        }

        URI uri = parseStrictUri(trimmed);
        validateAllowedDriveUri(uri);
        return uri.toASCIIString();
    }

    public static URI parseStrictUri(String rawUrl) {
        try {
            return new URI(rawUrl);
        } catch (URISyntaxException ex) {
            throw new BadRequestException(
                    "Please provide a valid HTTPS Google Drive link (https://drive.google.com/... or https://docs.google.com/...)."
            );
        }
    }

    public static void validateAllowedDriveUri(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || !"https".equalsIgnoreCase(scheme)) {
            throw new BadRequestException("Google Drive link must use HTTPS.");
        }

        if (uri.getRawUserInfo() != null) {
            throw new BadRequestException("Google Drive link must not contain embedded credentials.");
        }

        int port = uri.getPort();
        if (port != -1 && port != 443) {
            throw new BadRequestException("Google Drive link must use the standard HTTPS port.");
        }

        String host = uri.getHost();
        if (host == null || !ALLOWED_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException(
                    "Only official Google Drive or Google Docs links (drive.google.com or docs.google.com) are accepted."
            );
        }

        String path = uri.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            throw new BadRequestException("Please provide the full shareable link to your Drive folder or file.");
        }

        boolean matchesPath = ALLOWED_PATH_PREFIXES.stream().anyMatch(path::startsWith);
        if (!matchesPath) {
            throw new BadRequestException(
                    "Please paste a valid shareable Google Drive file or folder link."
            );
        }
    }

    public static boolean isAllowedRedirectHost(String host) {
        if (host == null) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        return ALLOWED_HOSTS.contains(lower) || lower.endsWith(".googleusercontent.com");
    }

    public static boolean isGoogleSignInHost(String host) {
        return host != null && "accounts.google.com".equalsIgnoreCase(host);
    }

    public static boolean isPrivateOrReservedAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        if (address instanceof Inet4Address ipv4) {
            byte[] bytes = ipv4.getAddress();
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            // Carrier-grade NAT 100.64.0.0/10, 0.0.0.0/8, benchmark 198.18.0.0/15
            if (first == 0 || (first == 100 && second >= 64 && second <= 127)
                    || (first == 198 && (second == 18 || second == 19))) {
                return true;
            }
        } else if (address instanceof Inet6Address ipv6) {
            byte[] bytes = ipv6.getAddress();
            int first = Byte.toUnsignedInt(bytes[0]);
            // Unique Local Address fc00::/7
            if ((first & 0xFE) == 0xFC) {
                return true;
            }
        }
        return false;
    }
}
