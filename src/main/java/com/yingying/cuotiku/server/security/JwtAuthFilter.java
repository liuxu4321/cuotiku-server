package com.yingying.cuotiku.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.repository.UserRepository;
import com.yingying.cuotiku.server.service.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    private record AuthResult(AuthenticatedUser principal, int code, String message) {}

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean anonymousFallback = request.getRequestURI().startsWith("/api/client/keepalive");
        if (log.isDebugEnabled()) {
            log.debug("[认证过滤器] {} {} 携带令牌={}", request.getMethod(), request.getRequestURI(),
                    header != null && header.startsWith("Bearer "));
        }
        if (header != null && header.startsWith("Bearer ")) {
            AuthResult result = tryAuthenticate(header.substring(7), request);
            if (result.principal() != null) {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        result.principal(), null, result.principal().getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else if (!anonymousFallback) {
                writeError(response, result.code(), result.message());
                return;
            } else if (log.isDebugEnabled()) {
                log.debug("[认证过滤器] 心跳接口令牌无效，降级为匿名上报 code={}", result.code());
            }
        }
        chain.doFilter(request, response);
    }

    private AuthResult tryAuthenticate(String token, HttpServletRequest request) {
        try {
            Claims claims = jwtService.parse(token);
            if (log.isDebugEnabled()) {
                log.debug("[认证过滤器] 令牌解析成功 sub={} jti={} typ={}",
                        claims.getSubject(), claims.getId(), claims.get("typ", String.class));
            }
            if (!JwtService.TYPE_ACCESS.equals(claims.get("typ", String.class))) {
                log.warn("[认证过滤器] 令牌类型错误 sub={} typ={} uri={}",
                        claims.getSubject(), claims.get("typ", String.class), request.getRequestURI());
                return new AuthResult(null, 401, "登录凭证类型不正确，请重新登录");
            }
            Optional<User> found = userRepository.findByPhone(claims.getSubject());
            if (found.isEmpty()) {
                return new AuthResult(null, 401, "账号不存在或已被删除");
            }
            User user = found.get();
            if (!user.isEnabled()) {
                return new AuthResult(null, 403, "账号已被停用");
            }
            if (user.isCancelled()) {
                return new AuthResult(null, 401, "登录凭证无效或已过期，请重新登录");
            }
            String jti = claims.getId();
            if (user.getSessionJti() == null) {
                log.debug("[认证过滤器] 会话已失效（密码修改/重置/登出） phone={} uri={}",
                        user.getPhone(), request.getRequestURI());
                return new AuthResult(null, 401, "登录凭证无效或已过期，请重新登录");
            }
            if (user.getSessionJti().equals(jti)) {
                // 当前会话，放行
            } else if (jti.equals(user.getPrevSessionJti())
                    && "REFRESH".equals(user.getRotateReason())
                    && AuthService.withinGrace(user)) {
                // 刷新轮换后的宽限期：并发在途旧令牌不误报踢出
                log.debug("[认证过滤器] 宽限期内旧令牌放行 phone={} uri={}", user.getPhone(), request.getRequestURI());
            } else if ("LOGIN".equals(user.getRotateReason())) {
                log.warn("[认证过滤器] 会话已被踢出 phone={} 令牌jti={} 当前sessionJti={} uri={}",
                        user.getPhone(), jti, user.getSessionJti(), request.getRequestURI());
                return new AuthResult(null, 4011,
                        "该账号已在其他设备登录，当前设备已退出。如非本人操作，请立即修改密码");
            } else {
                log.debug("[认证过滤器] 令牌已过期（轮换宽限已过） phone={} uri={}",
                        user.getPhone(), request.getRequestURI());
                return new AuthResult(null, 401, "登录凭证无效或已过期，请重新登录");
            }
            if (log.isDebugEnabled()) {
                log.debug("[认证过滤器] 认证通过 phone={} role={} jti={} uri={}",
                        user.getPhone(), user.getRole(), jti, request.getRequestURI());
            }
            return new AuthResult(
                    new AuthenticatedUser(user, jti, claims.getExpiration().toInstant()), 0, null);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("[认证过滤器] 令牌校验失败 uri={} 原因={}", request.getRequestURI(), e.getMessage());
            return new AuthResult(null, 401, "登录凭证无效或已过期，请重新登录");
        }
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code == 4011 ? 401 : code);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(code, message)));
    }
}
