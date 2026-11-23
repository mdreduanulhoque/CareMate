package com.carebridge.caretaker.drive;

import com.carebridge.common.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleDriveUrlValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://drive.google.com/file/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/view?usp=sharing",
            "https://drive.google.com/drive/folders/1FolderId123456789?usp=sharing",
            "https://drive.google.com/open?id=123456789",
            "https://docs.google.com/document/d/1DocId12345/edit?usp=sharing"
    })
    void acceptsOfficialHttpsGoogleDriveLinks(String validUrl) {
        String normalized = GoogleDriveUrlValidator.validateAndNormalize("  " + validUrl + "  ");
        assertThat(normalized).isEqualTo(validUrl);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "http://drive.google.com/file/d/123/view",
            "https://drive.google.com/",
            "https://drive.google.com",
            "https://drive.google.com/evil/path",
            "https://drive.google.com:8443/file/d/123/view",
            "https://user:pass@drive.google.com/file/d/123/view",
            "https://drive.google.com.attacker.com/file/d/123/view",
            "https://127.0.0.1/file/d/123/view",
            "https://169.254.169.254/latest/meta-data/",
            "file:///etc/passwd",
            "javascript:alert(1)"
    })
    void rejectsNonHttpsSpoofedOrUnsafeUrls(String unsafeUrl) {
        assertThatThrownBy(() -> GoogleDriveUrlValidator.validateAndNormalize(unsafeUrl))
                .isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1",
            "10.0.0.5",
            "172.16.0.1",
            "192.168.1.10",
            "169.254.169.254",
            "100.64.0.1",
            "0.0.0.0",
            "::1",
            "fc00::1"
    })
    void blocksPrivateLoopbackAndLinkLocalAddresses(String ip) throws Exception {
        InetAddress address = InetAddress.getByName(ip);
        assertThat(GoogleDriveUrlValidator.isPrivateOrReservedAddress(address)).isTrue();
    }

    @Test
    void allowsPublicInternetAddress() throws Exception {
        InetAddress publicGoogleIp = InetAddress.getByName("142.250.190.78");
        assertThat(GoogleDriveUrlValidator.isPrivateOrReservedAddress(publicGoogleIp)).isFalse();
    }
}
