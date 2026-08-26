package com.localexplorer.service;

import com.localexplorer.constant.ErrorCode;
import com.localexplorer.exception.BaseException;
import com.localexplorer.properties.AuthSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRequestSecurityTest {

    @Test
    void rejectsUntrustedBrowserOrigin() {
        AuthRequestSecurity security = new AuthRequestSecurity(new AuthSecurityProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.addHeader("Origin", "https://evil.example");

        assertThatThrownBy(() -> security.validateOrigin(request))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void ignoresForwardedIpUntilTrustedProxyIsExplicitlyEnabled() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.setFingerprintSecret("test-secret");
        AuthRequestSecurity security = new AuthRequestSecurity(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        String direct = security.ipHash(request);
        properties.setTrustedProxyEnabled(true);
        String forwarded = security.ipHash(request);

        assertThat(direct).isNotEqualTo(forwarded);
        assertThat(direct).hasSize(64);
        assertThat(forwarded).hasSize(64);
    }

    @Test
    void refreshCookiesAreHttpOnlyScopedAndSecureWhenConfigured() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.setCookieSecure(true);
        AuthRequestSecurity security = new AuthRequestSecurity(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        security.writeRefreshCookie(response, "EMPLOYEE", "secret-refresh");

        assertThat(response.getHeader("Set-Cookie"))
                .contains("LX_ADMIN_REFRESH=secret-refresh", "Path=/admin", "HttpOnly", "Secure", "SameSite=Lax")
                .doesNotContain("localStorage");
    }
}
