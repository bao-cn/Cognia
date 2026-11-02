package bao.gabriel.cognia.security;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.common.util.WebUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the Bearer token, validates it against Redis and populates the
 * SecurityContext. Invalid tokens are not written directly from the filter:
 * the authentication entry point produces the JSON error, so public endpoints
 * (login, register, refresh) keep working even with a stale token attached.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = WebUtils.bearerToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                LoginUser loginUser = tokenService.authenticate(token);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BusinessException ex) {
                SecurityContextHolder.clearContext();
                request.setAttribute(RestAuthenticationEntryPoint.ATTR_ERROR_CODE, ex.getResultCode());
                request.setAttribute(RestAuthenticationEntryPoint.ATTR_ERROR_MESSAGE, ex.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
