package com.yingying.cuotiku.server.mini;

import com.yingying.cuotiku.server.security.AuthenticatedUser;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

/** 统一审计小程序管理写接口；仅记录方法、资源路径和结果码。 */
@Configuration
public class MiniAuditConfiguration implements WebMvcConfigurer {
  private final MiniAuditService audit;
  private final com.fasterxml.jackson.databind.ObjectMapper json;

  public MiniAuditConfiguration(
      MiniAuditService audit, com.fasterxml.jackson.databind.ObjectMapper json) {
    this.audit = audit;
    this.json = json;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(
            new HandlerInterceptor() {
              @Override
              public boolean preHandle(
                  HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!java.util.Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) {
                  var auth = SecurityContextHolder.getContext().getAuthentication();
                  if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user)
                    request.setAttribute(
                        "miniAuditId",
                        audit.begin(
                            user.user().getId(), request.getMethod(), request.getRequestURI()));
                }
                return true;
              }

              @Override
              public void afterCompletion(
                  HttpServletRequest request,
                  HttpServletResponse response,
                  Object handler,
                  Exception exception) {
                Object id = request.getAttribute("miniAuditId");
                if (id != null) {
                  String reason = null;
                  if (request
                      instanceof org.springframework.web.util.ContentCachingRequestWrapper wrapper)
                    try {
                      var body = json.readTree(wrapper.getContentAsByteArray());
                      if (body != null && body.path("reason").isTextual())
                        reason = body.path("reason").asText();
                    } catch (Exception ignored) {
                    }
                  audit.finish(id.toString(), response.getStatus(), reason);
                }
              }
            })
        .addPathPatterns("/api/admin/mini/v1/**");
  }
}
