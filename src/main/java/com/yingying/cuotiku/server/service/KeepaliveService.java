package com.yingying.cuotiku.server.service;

import com.yingying.cuotiku.server.dto.AdminUserDto.KeepaliveDto;
import com.yingying.cuotiku.server.dto.AdminUserDto.PageResponse;
import com.yingying.cuotiku.server.dto.AuthDto.KeepaliveRequest;
import com.yingying.cuotiku.server.entity.ClientKeepalive;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.repository.ClientKeepaliveRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class KeepaliveService {

    private static final Logger log = LoggerFactory.getLogger(KeepaliveService.class);
    private static final Duration ONLINE_WINDOW = Duration.ofSeconds(150);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ClientKeepaliveRepository repository;

    public KeepaliveService(ClientKeepaliveRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void report(KeepaliveRequest request, User user) {
        Instant now = Instant.now();
        ClientKeepalive row = repository.findById(request.clientId()).orElseGet(() -> {
            ClientKeepalive created = new ClientKeepalive();
            created.setClientId(request.clientId());
            created.setFirstSeenAt(now);
            return created;
        });
        row.setLoggedIn(user != null);
        row.setUserId(user == null ? null : user.getId());
        row.setPhone(user == null ? null : user.getPhone());
        row.setAppVersion(trimTo(request.appVersion(), 32));
        row.setPlatform(trimTo(request.platform(), 32));
        row.setOsVersion(trimTo(request.osVersion(), 64));
        row.setState(trimTo(request.state(), 32));
        row.setDetail(trimTo(request.detail(), 256));
        row.setReportCount(row.getReportCount() + 1);
        row.setLastSeenAt(now);
        repository.save(row);
        if (log.isDebugEnabled()) {
            log.debug("[心跳] clientId={} 用户={} state={} 累计={}次",
                    request.clientId(), user == null ? "临时用户" : user.getPhone(),
                    request.state(), row.getReportCount());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<KeepaliveDto> list(boolean onlineOnly, String keyword, int page, int size) {
        Instant onlineSince = Instant.now().minus(ONLINE_WINDOW);
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200),
                Sort.by(Sort.Direction.DESC, "lastSeenAt"));
        Page<ClientKeepalive> result = repository.search(onlineOnly, onlineSince, kw, pageable);
        List<KeepaliveDto> items = result.getContent().stream()
                .map(row -> toDto(row, onlineSince)).toList();
        return new PageResponse<>(items, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    private KeepaliveDto toDto(ClientKeepalive row, Instant onlineSince) {
        return new KeepaliveDto(
                row.getClientId(),
                row.getPhone(),
                row.isLoggedIn(),
                row.getAppVersion(),
                row.getPlatform(),
                row.getOsVersion(),
                row.getState(),
                row.getDetail(),
                row.getReportCount(),
                row.getFirstSeenAt() == null ? null : TIME_FORMAT.format(row.getFirstSeenAt()),
                row.getLastSeenAt() == null ? null : TIME_FORMAT.format(row.getLastSeenAt()),
                row.getLastSeenAt() != null && !row.getLastSeenAt().isBefore(onlineSince));
    }

    private String trimTo(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
