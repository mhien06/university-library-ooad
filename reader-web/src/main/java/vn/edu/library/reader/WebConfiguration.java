package vn.edu.library.reader;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import vn.edu.library.reader.service.ReaderAuthService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final ReaderAuthService auth;

    public WebConfiguration(ReaderAuthService auth) { this.auth = auth; }

    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ReaderSessionInterceptor(auth));
    }

    static String csrf(HttpSession session) {
        String token = (String) session.getAttribute("csrfToken");
        if (token == null) { token = UUID.randomUUID().toString(); session.setAttribute("csrfToken", token); }
        return token;
    }

    private static final class ReaderSessionInterceptor implements HandlerInterceptor {
        private final ReaderAuthService auth;
        private ReaderSessionInterceptor(ReaderAuthService auth) { this.auth = auth; }

        @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            HttpSession session = request.getSession(true);
            csrf(session);
            String path = request.getRequestURI();
            boolean publicPath = path.equals("/") || path.equals("/login") || path.equals("/register") || path.equals("/rules") || path.startsWith("/css/") || path.equals("/favicon.ico");
            if (!publicPath) {
                Object id = session.getAttribute("accountId");
                if (!(id instanceof Long accountId) || !auth.isReaderSessionActive(accountId)) {
                    session.invalidate(); response.sendRedirect("/login?expired=1"); return false;
                }
            }
            if ("POST".equalsIgnoreCase(request.getMethod())) {
                String expected = (String) session.getAttribute("csrfToken");
                String supplied = request.getParameter("_csrf");
                if (expected == null || supplied == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Yêu cầu không hợp lệ. Hãy tải lại trang rồi thử lại."); return false;
                }
            }
            return true;
        }
    }
}
