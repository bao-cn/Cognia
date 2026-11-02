package bao.gabriel.cognia.security;

import bao.gabriel.cognia.common.api.ResultCode;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Returns the standard JSON envelope with 401 instead of an HTML login page.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String ATTR_ERROR_CODE = "cognia.auth.errorCode";
    public static final String ATTR_ERROR_MESSAGE = "cognia.auth.errorMessage";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ResultCode resultCode = ResultCode.UNAUTHORIZED;
        Object attrCode = request.getAttribute(ATTR_ERROR_CODE);
        if (attrCode instanceof ResultCode code) {
            resultCode = code;
        }

        String message = null;
        Object attrMessage = request.getAttribute(ATTR_ERROR_MESSAGE);
        if (attrMessage instanceof String text && !text.isBlank()) {
            message = text;
        }
        if (message == null || message.isBlank()) {
            message = resultCode.getMessage();
        }

        write(response, resultCode, message);
    }

    public static void write(HttpServletResponse response, ResultCode resultCode, String message) throws IOException {
        response.setStatus(resultCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        JSONObject body = new JSONObject();
        body.put("code", resultCode.getCode());
        body.put("message", message);
        body.put("data", null);
        body.put("timestamp", System.currentTimeMillis());
        response.getWriter().write(body.toJSONString());
    }
}
