package com.atomichabits.backend.security;

import com.atomichabits.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Pins every AG-UI run to the authenticated user's own thread ({@code user-<id>}).
 *
 * <p>The AG-UI protocol lets the client choose {@code threadId}, and the coach resolves the acting
 * user from that thread because tools run on the starter's worker threads, where no security context
 * exists. Trusting the client value would let any signed-in user resume another user's conversation
 * and act on their data, so the value is always overwritten server-side.</p>
 */
public class AguiThreadBindingFilter extends OncePerRequestFilter {

    public static final String THREAD_PREFIX = "user-";

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final String pathPrefix;

    public AguiThreadBindingFilter(UserRepository userRepository, ObjectMapper objectMapper, String pathPrefix) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.pathPrefix = pathPrefix;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !request.getRequestURI().startsWith(request.getContextPath() + pathPrefix);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = auth == null || !auth.isAuthenticated()
                ? null
                : userRepository.findByEmail(auth.getName()).map(u -> u.getId()).orElse(null);
        if (userId == null) {
            // Unauthenticated: leave the request untouched; authorization rules reject it.
            chain.doFilter(request, response);
            return;
        }

        byte[] body = request.getInputStream().readAllBytes();
        if (body.length > 0) {
            var node = objectMapper.readTree(body);
            if (node instanceof ObjectNode object) {
                object.put("threadId", THREAD_PREFIX + userId);
                body = objectMapper.writeValueAsBytes(object);
            }
        }
        chain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
