package com.web.project.auth.protection;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeaderFromUntrustedPeer() {
        HttpServletRequest request = request("10.0.0.9", "203.0.113.10");
        ClientIpResolver resolver = new ClientIpResolver(properties(List.of("10.0.0.8")));

        assertEquals("10.0.0.9", resolver.resolve(request));
    }

    @Test
    void usesSingleForwardedAddressFromConfiguredProxy() {
        HttpServletRequest request = request("10.0.0.8", "203.0.113.10");
        ClientIpResolver resolver = new ClientIpResolver(properties(List.of("10.0.0.8")));

        assertEquals("203.0.113.10", resolver.resolve(request));
    }

    @Test
    void rejectsInjectedForwardedChainEvenFromTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver(properties(List.of("172.30.80.10")));
        assertEquals("172.30.80.10", resolver.resolve(
                request("172.30.80.10", "198.51.100.7, 198.51.100.8")));
        assertEquals("172.30.80.10", resolver.resolve(
                request("172.30.80.10", "invalid.example")));
        assertEquals("172.30.80.10", resolver.resolve(
                request("172.30.80.10", "999.2.3.4")));
        assertEquals("172.30.80.10", resolver.resolve(
                request("172.30.80.10", "198.51.100.7:4000")));
    }

    @Test
    void differentExternalClientsGetDifferentRateLimitIdentities() {
        ClientIpResolver resolver = new ClientIpResolver(properties(List.of("172.30.80.10")));
        assertEquals("198.51.100.7", resolver.resolve(
                request("172.30.80.10", "198.51.100.7")));
        assertEquals("198.51.100.8", resolver.resolve(
                request("172.30.80.10", "198.51.100.8")));
        // An untrusted container cannot impersonate the public client's bucket.
        assertEquals("172.30.80.20", resolver.resolve(
                request("172.30.80.20", "198.51.100.7")));
    }

    @Test
    void acceptsValidIpv6ClientFromTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver(properties(List.of("172.30.80.10")));
        assertEquals("2001:db8::1", resolver.resolve(
                request("172.30.80.10", "2001:db8::1")));
    }

    @Test
    void fallsBackToProxyAddressWhenForwardedHeaderIsMissing() {
        HttpServletRequest request = request("10.0.0.8", null);
        ClientIpResolver resolver = new ClientIpResolver(properties(List.of("10.0.0.8")));

        assertEquals("10.0.0.8", resolver.resolve(request));
    }

    private static HttpServletRequest request(String remoteAddress, String forwardedFor) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remoteAddress);
        when(request.getHeader("X-Forwarded-For")).thenReturn(forwardedFor);
        return request;
    }

    private static LoginProtectionProperties properties(List<String> trustedProxies) {
        return new LoginProtectionProperties(1, Duration.ofMinutes(1), 60, 20, trustedProxies);
    }
}
