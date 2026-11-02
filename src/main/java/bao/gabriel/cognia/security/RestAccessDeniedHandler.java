package bao.gabriel.cognia.security;

import bao.gabriel.cognia.common.api.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns the standard JSON envelope with 403 when an authenticated user lacks
 * the required role or permission.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        RestAuthenticationEntryPoint.write(response, ResultCode.FORBIDDEN,
                ResultCode.FORBIDDEN.getMessage());
    }
}
