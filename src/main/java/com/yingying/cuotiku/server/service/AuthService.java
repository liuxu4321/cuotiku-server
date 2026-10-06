package com.yingying.cuotiku.server.service;

import com.yingying.cuotiku.server.dto.AuthDto.*;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.repository.UserRepository;
import com.yingying.cuotiku.server.security.JwtService;
import com.yingying.cuotiku.server.web.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class AuthService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthService.class);

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CaptchaService captchaService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, CaptchaService captchaService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.captchaService = captchaService;
    }

    public CaptchaResponse captcha() {
        CaptchaService.Captcha captcha = captchaService.generate();
        if (log.isDebugEnabled()) {
            log.debug("[登录] 下发验证码 captchaId={}", captcha.captchaId());
        }
        return new CaptchaResponse(captcha.captchaId(), captcha.imageBase64(), captcha.expiresInSeconds());
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        if (log.isDebugEnabled()) {
            log.debug("[登录] 开始 phone={} captchaId={} clientLabel={}",
                    request.phone(), request.captchaId(), request.clientLabel());
        }
        if (!captchaService.verifyAndConsume(request.captchaId(), request.captchaCode())) {
            log.debug("[登录] 失败：验证码错误 phone={} captchaId={}", request.phone(), request.captchaId());
            throw ApiException.badRequest("验证码错误或已过期，请重新获取");
        }
        User user = userRepository.findByPhone(request.phone()).orElse(null);
        if (user == null) {
            log.debug("[登录] 失败：账号不存在 phone={}", request.phone());
            throw ApiException.unauthorized("手机号或密码错误");
        }
        if (user.isCancelled()) {
            log.debug("[登录] 失败：账号已注销 phone={} id={}", request.phone(), user.getId());
            throw ApiException.unauthorized("手机号或密码错误");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            log.debug("[登录] 失败：密码错误 phone={} id={}", request.phone(), user.getId());
            throw ApiException.unauthorized("手机号或密码错误");
        }
        if (!user.isEnabled()) {
            log.debug("[登录] 失败：账号停用 phone={} id={}", request.phone(), user.getId());
            throw ApiException.forbidden("账号已被停用，请联系管理员");
        }
        if (log.isDebugEnabled() && user.getSessionJti() != null) {
            log.debug("[登录] 踢出旧会话 phone={} 旧sessionJti={}", user.getPhone(), user.getSessionJti());
        }
        user.setPrevSessionJti(user.getSessionJti());
        user.setPrevRefreshJti(user.getRefreshJti());
        user.setRotateReason("LOGIN");
        user.setRotatedAt(Instant.now());
        JwtService.IssuedToken access = jwtService.issueAccess(user.getPhone(), user.getRole().name());
        JwtService.IssuedToken refresh = jwtService.issueRefresh(user.getPhone());
        user.setSessionJti(access.jti());
        user.setRefreshJti(refresh.jti());
        user.setLastLoginAt(Instant.now());
        if (request.clientLabel() != null && !request.clientLabel().isBlank()) {
            user.setClientLabel(request.clientLabel().trim());
        }
        userRepository.save(user);
        if (log.isDebugEnabled()) {
            log.debug("[登录] 成功 phone={} id={} role={} accessJti={} refreshJti={} 访问令牌={}s 刷新令牌={}s",
                    user.getPhone(), user.getId(), user.getRole(), access.jti(), refresh.jti(),
                    jwtService.ttlSeconds(), jwtService.refreshTtlSeconds());
        }
        return new LoginResponse(access.token(), jwtService.ttlSeconds(),
                refresh.token(), jwtService.refreshTtlSeconds(), toDto(user));
    }

    @Transactional
    public RefreshResponse refresh(RefreshRequest request) {
        io.jsonwebtoken.Claims claims;
        try {
            claims = jwtService.parse(request.refreshToken());
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            throw ApiException.unauthorized("刷新凭证无效或已过期，请重新登录");
        }
        if (log.isDebugEnabled()) {
            log.debug("[刷新] 令牌解析成功 sub={} jti={} typ={}",
                    claims.getSubject(), claims.getId(), claims.get("typ", String.class));
        }
        if (!JwtService.TYPE_REFRESH.equals(claims.get("typ", String.class))) {
            log.debug("[刷新] 失败：凭证类型错误 sub={}", claims.getSubject());
            throw ApiException.unauthorized("凭证类型不正确，请重新登录");
        }
        User user = userRepository.findByPhone(claims.getSubject()).orElse(null);
        if (user == null) {
            log.debug("[刷新] 失败：账号不存在 sub={}", claims.getSubject());
            throw ApiException.unauthorized("账号不存在或已被删除");
        }
        if (user.isCancelled()) {
            log.debug("[刷新] 失败：账号已注销 phone={}", user.getPhone());
            throw ApiException.unauthorized("登录已失效，请重新登录");
        }
        if (!user.isEnabled()) {
            log.debug("[刷新] 失败：账号停用 phone={}", user.getPhone());
            throw ApiException.forbidden("账号已被停用，请联系管理员");
        }
        if (user.getRefreshJti() != null && user.getRefreshJti().equals(claims.getId())) {
            user.setPrevSessionJti(user.getSessionJti());
            user.setPrevRefreshJti(user.getRefreshJti());
            user.setRotateReason("REFRESH");
            user.setRotatedAt(Instant.now());
            JwtService.IssuedToken access = jwtService.issueAccess(user.getPhone(), user.getRole().name());
            JwtService.IssuedToken refresh = jwtService.issueRefresh(user.getPhone());
            user.setSessionJti(access.jti());
            user.setRefreshJti(refresh.jti());
            userRepository.save(user);
            if (log.isDebugEnabled()) {
                log.debug("[刷新] 成功并轮换令牌 phone={} 新accessJti={} 新refreshJti={}",
                        user.getPhone(), access.jti(), refresh.jti());
            }
            return new RefreshResponse(access.token(), jwtService.ttlSeconds(),
                    refresh.token(), jwtService.refreshTtlSeconds());
        }
        // 并发刷新竞态：旧刷新令牌在宽限期内 → 按当前会话重发令牌，不再轮换
        if (user.getPrevRefreshJti() != null && user.getPrevRefreshJti().equals(claims.getId())
                && "REFRESH".equals(user.getRotateReason()) && withinGrace(user)) {
            JwtService.IssuedToken access =
                    jwtService.issueAccessWithJti(user.getPhone(), user.getRole().name(), user.getSessionJti());
            JwtService.IssuedToken refresh =
                    jwtService.issueRefreshWithJti(user.getPhone(), user.getRefreshJti());
            log.debug("[刷新] 宽限期内并发刷新，重发当前会话令牌 phone={}", user.getPhone());
            return new RefreshResponse(access.token(), jwtService.ttlSeconds(),
                    refresh.token(), jwtService.refreshTtlSeconds());
        }
        if ("LOGIN".equals(user.getRotateReason())) {
            log.debug("[刷新] 失败：账号已在其他设备登录 phone={}", user.getPhone());
            throw ApiException.unauthorized("该账号已在其他设备登录，请重新登录");
        }
        log.debug("[刷新] 失败：刷新凭证已失效 phone={}", user.getPhone());
        throw ApiException.unauthorized("登录已失效，请重新登录");
    }

    @Transactional
    public void changePassword(User user, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            log.debug("[改密] 失败：原密码错误 phone={}", user.getPhone());
            throw ApiException.badRequest("原密码错误");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw ApiException.badRequest("新密码不能与原密码相同");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setSessionJti(null);
        user.setRefreshJti(null);
        userRepository.save(user);
        log.info("[改密] 密码已修改并踢下线 phone={} id={}", user.getPhone(), user.getId());
    }

    public static boolean withinGrace(User user) {
        return user.getRotatedAt() != null
                && java.time.Duration.between(user.getRotatedAt(), Instant.now()).getSeconds()
                <= JwtService.ROTATION_GRACE_SECONDS;
    }

    @Transactional(readOnly = true)
    public MeResponse me(User user, Instant tokenExpiresAt) {
        return new MeResponse(
                user.getPhone(),
                user.getMemberNo(),
                user.getMemberExpireAt() == null ? null : user.getMemberExpireAt().toString(),
                user.isMemberActive(),
                user.getRole().name(),
                user.isAiEnabled(),
                tokenExpiresAt == null ? null : TIME_FORMAT.format(tokenExpiresAt));
    }

    public static UserDto toDto(User user) {
        return new UserDto(
                user.getId(),
                user.getPhone(),
                user.getMemberNo(),
                user.getMemberExpireAt() == null ? null : user.getMemberExpireAt().toString(),
                user.isMemberActive(),
                user.getRole().name(),
                user.isAiEnabled(),
                user.isEnabled(),
                user.isCancelled(),
                user.getClientLabel(),
                user.getLastLoginAt() == null ? null : TIME_FORMAT.format(user.getLastLoginAt()),
                user.getCreatedAt() == null ? null : TIME_FORMAT.format(user.getCreatedAt()));
    }
}
