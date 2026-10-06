package com.yingying.cuotiku.server.service;

import com.yingying.cuotiku.server.dto.AdminUserDto.*;
import com.yingying.cuotiku.server.dto.AuthDto.UserDto;
import com.yingying.cuotiku.server.entity.Role;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.repository.UserRepository;
import com.yingying.cuotiku.server.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class AdminUserService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminUserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDto> list(String keyword, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200),
                Sort.by(Sort.Direction.DESC, "id"));
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Page<User> result = userRepository.search(kw, pageable);
        List<UserDto> items = result.getContent().stream().map(AuthService::toDto).toList();
        if (log.isDebugEnabled()) {
            log.debug("[用户管理] 查询 keyword={} page={} size={} → total={}", kw, page, size, result.getTotalElements());
        }
        return new PageResponse<>(items, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public UserDto get(Long id) {
        return AuthService.toDto(require(id));
    }

    @Transactional
    public UserDto create(CreateUserRequest request, User operator) {
        if (userRepository.existsByPhone(request.phone())) {
            throw ApiException.conflict("手机号已存在：" + request.phone());
        }
        User user = new User();
        user.setPhone(request.phone());
        user.setMemberNo(blankToNull(request.memberNo()));
        user.setMemberExpireAt(request.memberExpireAt() == null || request.memberExpireAt().isBlank()
                ? LocalDate.now().plusYears(1)
                : parseDate(request.memberExpireAt()));
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setAiEnabled(Boolean.TRUE.equals(request.aiEnabled()));
        user.setEnabled(request.enabled() == null || request.enabled());
        user.setRole(parseRole(request.role(), Role.USER));
        userRepository.save(user);
        if (log.isDebugEnabled()) {
            log.debug("[用户管理] 创建 id={} phone={} memberNo={} role={} aiEnabled={} enabled={} memberExpireAt={} 操作人={}",
                    user.getId(), user.getPhone(), user.getMemberNo(), user.getRole(),
                    user.isAiEnabled(), user.isEnabled(), user.getMemberExpireAt(), operator.getPhone());
        }
        return AuthService.toDto(user);
    }

    @Transactional
    public UserDto update(Long id, UpdateUserRequest request, User operator) {
        User user = require(id);
        if (request.memberNo() != null) {
            user.setMemberNo(blankToNull(request.memberNo()));
        }
        if (request.memberExpireAt() != null) {
            user.setMemberExpireAt(request.memberExpireAt().isBlank()
                    ? null
                    : parseDate(request.memberExpireAt()));
        }
        // 重新设置会员号或有效期即视为恢复已注销账号
        if (user.isCancelled() && (user.getMemberNo() != null || user.getMemberExpireAt() != null)) {
            user.setCancelled(false);
            log.debug("[用户管理] 已注销账号恢复 id={}（重新设置了会员信息）", id);
        }
        if (request.aiEnabled() != null) {
            user.setAiEnabled(request.aiEnabled());
        }
        if (request.enabled() != null) {
            if (user.getId().equals(operator.getId()) && !request.enabled()) {
                throw ApiException.badRequest("不能停用自己的账号");
            }
            user.setEnabled(request.enabled());
            if (!request.enabled()) {
                user.setSessionJti(null);
                user.setRefreshJti(null);
            }
        }
        if (request.role() != null && !request.role().isBlank()) {
            Role role = parseRole(request.role(), user.getRole());
            if (user.getId().equals(operator.getId()) && role != Role.ADMIN) {
                throw ApiException.badRequest("不能取消自己的管理员角色");
            }
            user.setRole(role);
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
            user.setSessionJti(null);
            user.setRefreshJti(null);
            log.debug("[用户管理] 更新时重置密码并踢下线 id={}", id);
        }
        userRepository.save(user);
        if (log.isDebugEnabled()) {
            log.debug("[用户管理] 更新 id={} memberNo={} role={} aiEnabled={} enabled={} memberExpireAt={} 改密={} 操作人={}",
                    user.getId(), user.getMemberNo(), user.getRole(), user.isAiEnabled(), user.isEnabled(),
                    user.getMemberExpireAt(), request.password() != null && !request.password().isBlank(),
                    operator.getPhone());
        }
        return AuthService.toDto(user);
    }

    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request, User operator) {
        User user = require(id);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setSessionJti(null);
        user.setRefreshJti(null);
        userRepository.save(user);
        log.debug("[用户管理] 重置密码并踢下线 id={} phone={} 操作人={}", id, user.getPhone(), operator.getPhone());
    }

    @Transactional
    public UserDto cancelMembership(Long id, User operator) {
        User user = require(id);
        if (user.getId().equals(operator.getId())) {
            throw ApiException.badRequest("不能注销自己的账号");
        }
        user.setMemberNo(null);
        user.setMemberExpireAt(null);
        user.setAiEnabled(false);
        user.setCancelled(true);
        user.setSessionJti(null);
        user.setRefreshJti(null);
        userRepository.save(user);
        log.debug("[用户管理] 一键注销会员 id={} phone={} 操作人={}（已踢下线并禁止登录）",
                id, user.getPhone(), operator.getPhone());
        return AuthService.toDto(user);
    }

    @Transactional
    public void delete(Long id, User operator) {
        User user = require(id);
        if (user.getId().equals(operator.getId())) {
            throw ApiException.badRequest("不能删除自己的账号");
        }
        if (user.getRole() == Role.ADMIN && userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN).count() <= 1) {
            throw ApiException.badRequest("系统至少需要保留一个管理员");
        }
        userRepository.delete(user);
        log.debug("[用户管理] 删除 id={} phone={} 操作人={}", id, user.getPhone(), operator.getPhone());
    }

    private User require(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("用户不存在：" + id));
    }

    private Role parseRole(String value, Role fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("角色取值只能是 USER 或 ADMIN");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("有效期格式不正确，应为 yyyy-MM-dd");
        }
    }
}
