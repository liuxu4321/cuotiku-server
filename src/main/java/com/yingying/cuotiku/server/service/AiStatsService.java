package com.yingying.cuotiku.server.service;

import com.yingying.cuotiku.server.dto.AdminUserDto.AiCallLogDto;
import com.yingying.cuotiku.server.dto.AdminUserDto.AiStatsResponse;
import com.yingying.cuotiku.server.dto.AdminUserDto.AiStatsSummary;
import com.yingying.cuotiku.server.entity.AiCallLog;
import com.yingying.cuotiku.server.repository.AiCallLogRepository;
import com.yingying.cuotiku.server.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

@Service
public class AiStatsService {

    private static final Set<String> AI_TYPES = Set.of("ERASE", "CROP_ENHANCE", "SPLIT_QUESTIONS", "ANALOGY", "EXPLAIN");
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final AiCallLogRepository repository;

    public AiStatsService(AiCallLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AiStatsResponse query(String phone, String aiType, String start, String end, int page, int size) {
        String phoneKw = phone == null || phone.isBlank() ? null : phone.trim();
        String type = aiType == null || aiType.isBlank() ? null : aiType.trim().toUpperCase();
        if (type != null && !AI_TYPES.contains(type)) {
            throw ApiException.badRequest("AI类型只能是：ERASE / CROP_ENHANCE / SPLIT_QUESTIONS / ANALOGY / EXPLAIN");
        }
        Instant startAt = parseDate(start, false);
        Instant endAt = parseDate(end, true);

        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200),
                Sort.by(Sort.Direction.DESC, "id"));
        Page<AiCallLog> result = repository.search(phoneKw, type, startAt, endAt, pageable);
        List<AiCallLogDto> items = result.getContent().stream().map(AiStatsService::toDto).toList();
        long success = repository.countSuccess(phoneKw, type, startAt, endAt);
        long failed = repository.countFailed(phoneKw, type, startAt, endAt);
        return new AiStatsResponse(items, result.getTotalElements(), result.getNumber(), result.getSize(),
                new AiStatsSummary(result.getTotalElements(), success, failed));
    }

    private Instant parseDate(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(value.trim());
            return endOfDay
                    ? date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1)
                    : date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("日期格式不正确，应为 yyyy-MM-dd");
        }
    }

    private static AiCallLogDto toDto(AiCallLog log) {
        return new AiCallLogDto(
                log.getId(),
                log.getPhone(),
                log.getAiType(),
                log.isSuccess(),
                log.getErrorCode(),
                log.getErrorMessage(),
                log.getTraceId(),
                log.getRequestId(),
                log.getInputBytes(),
                log.getOutputBytes(),
                log.getDurationMs(),
                log.getInputTokens(),
                log.getOutputTokens(),
                log.getCreatedAt() == null ? null : TIME_FORMAT.format(log.getCreatedAt()));
    }
}
