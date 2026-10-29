package com.web.project.auth.protection;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * A forwarded client IP is accepted only from a configured immediate proxy.
 * The proxy MUST overwrite (not append to) X-Forwarded-For with one validated
 * client address. In particular, untrusted clients cannot choose their rate-limit bucket.
 */
@Component
public class ClientIpResolver {

    private final LoginProtectionProperties properties;

    public ClientIpResolver(LoginProtectionProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!properties.trustedProxyAddresses().contains(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null) {
            return remoteAddress;
        }
        String client = forwardedFor.trim();
        // Multi-hop chains, hostnames, quoted/port-bearing values and malformed
        // IPs aren't valid for the single-hop trusted proxy contract.
        if (client.isEmpty() || client.contains(",") || !validLiteralIp(client)) {
            return remoteAddress;
        }

        return client;
    }

    private static boolean validLiteralIp(String value) {
        if (value.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) {
            for (String part : value.split("\\.")) {
                if (Integer.parseInt(part) > 255) {
                    return false;
                }
            }
            return true;
        }
        if (!value.contains(":") || !value.matches("[0-9a-fA-F:.]+")) {
            return false;
        }
        try {
            return InetAddress.getByName(value) instanceof Inet6Address;
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
