package com.yingying.cuotiku.server.service;

import com.yingying.cuotiku.server.dto.BookDto.AbilityDimensionDto;
import com.yingying.cuotiku.server.dto.BookDto.AbilityModelDto;
import com.yingying.cuotiku.server.dto.BookDto.AbilityResponseDto;
import com.yingying.cuotiku.server.entity.BookEntry;
import com.yingying.cuotiku.server.repository.BookEntryRepository;
import com.yingying.cuotiku.server.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 用户能力模型（能力五边形）。
 * 五个维度：细心度/理解力/概念清晰/规范度（由四类错题量反向决定）+ 练习勤奋度（刷题量正向）。
 * 时间衰减：w = 0.5^(距今天数/60)；短板 weakness = W/(W+5)；
 * 刷题缓解 relief = 0.6 * P/(P+10)；得分 = 100 * (1 - weakness * (1 - relief))。
 */
@Service
public class AbilityService {

    private static final Logger log = LoggerFactory.getLogger(AbilityService.class);
    private static final double HALF_LIFE_DAYS = 60.0;
    private static final double WEAKNESS_K = 5.0;
    private static final double RELIEF_MAX = 0.6;
    private static final double RELIEF_M = 10.0;
    private static final double DILIGENCE_D = 15.0;
    private static final List<String> SUBJECTS = List.of("语文", "数学", "英语");

    private record DimSpec(String key, String label, String errorType) {}

    private static final List<DimSpec> TYPE_DIMS = List.of(
            new DimSpec("CAREFULNESS", "细心度", "马虎"),
            new DimSpec("COMPREHENSION", "理解力", "不会"),
            new DimSpec("CONCEPT", "概念清晰", "概念不清"),
            new DimSpec("STANDARD", "规范度", "其他"));
    private static final DimSpec DILIGENCE = new DimSpec("DILIGENCE", "练习勤奋度", null);

    private final BookEntryRepository repository;

    public AbilityService(BookEntryRepository repository) {
        this.repository = repository;
    }

    public record Filters(Integer grade, Integer term, String subject, Instant start, Instant end) {}

    public static Filters parseFilters(Integer grade, Integer term, String subject, String start, String end) {
        String subj = subject == null || subject.isBlank() ? null : subject.trim();
        if (subj != null && !SUBJECTS.contains(subj)) {
            throw ApiException.badRequest("科目只能是：" + String.join("、", SUBJECTS));
        }
        return new Filters(grade, term, subj, parseDate(start, false), parseDate(end, true));
    }

    private static Instant parseDate(String value, boolean endOfDay) {
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

    @Transactional(readOnly = true)
    public AbilityResponseDto query(Long userId, Filters filters) {
        List<BookEntry> all = repository.findForAbility(userId, filters.grade(), filters.term(),
                filters.subject(), filters.start(), filters.end());
        List<AbilityModelDto> subjects = new ArrayList<>();
        if (filters.subject() != null) {
            subjects.add(compute(all, filters.subject()));
        } else {
            for (String subj : SUBJECTS) {
                List<BookEntry> part = all.stream().filter(e -> subj.equals(e.getSubject())).toList();
                subjects.add(compute(part, subj));
            }
        }
        AbilityModelDto overall = compute(all, null);
        if (log.isDebugEnabled()) {
            log.debug("[能力模型] userId={} 样本={} 综合={} 科目数={}",
                    userId, all.size(), overall.overall(), subjects.size());
        }
        return new AbilityResponseDto(overall, subjects);
    }

    public record AbilityCore(int sampleSize, Double overall, List<AbilityDimensionDto> dimensions) {}

    public AbilityCore computeCore(List<BookEntry> entries) {
        List<AbilityDimensionDto> dims = new ArrayList<>();
        if (entries.isEmpty()) {
            for (DimSpec spec : TYPE_DIMS) {
                dims.add(new AbilityDimensionDto(spec.key(), spec.label(), null, 0, 0, 0));
            }
            dims.add(new AbilityDimensionDto(DILIGENCE.key(), DILIGENCE.label(), null, 0, 0, 0));
            return new AbilityCore(0, null, dims);
        }
        long nowMillis = System.currentTimeMillis();
        double[] w = new double[TYPE_DIMS.size()];
        double[] p = new double[TYPE_DIMS.size()];
        int[] count = new int[TYPE_DIMS.size()];
        int[] practice = new int[TYPE_DIMS.size()];
        double pAll = 0;
        for (BookEntry e : entries) {
            int idx = -1;
            for (int i = 0; i < TYPE_DIMS.size(); i += 1) {
                if (TYPE_DIMS.get(i).errorType().equals(e.getErrorType())) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) {
                continue;
            }
            double ageDays = Math.max(0, (nowMillis - e.getCreatedAt().toEpochMilli()) / 86_400_000.0);
            double weight = Math.pow(0.5, ageDays / HALF_LIFE_DAYS);
            w[idx] += weight;
            p[idx] += e.getPracticeCount() * weight;
            count[idx] += 1;
            practice[idx] += e.getPracticeCount();
            pAll += e.getPracticeCount() * weight;
        }
        int scoreSum = 0;
        for (int i = 0; i < TYPE_DIMS.size(); i += 1) {
            double weakness = w[i] / (w[i] + WEAKNESS_K);
            double relief = RELIEF_MAX * p[i] / (p[i] + RELIEF_M);
            int score = (int) Math.round(100 * (1 - weakness * (1 - relief)));
            scoreSum += score;
            dims.add(new AbilityDimensionDto(TYPE_DIMS.get(i).key(), TYPE_DIMS.get(i).label(),
                    score, count[i], practice[i], round2(w[i])));
        }
        int diligence = (int) Math.round(100 * pAll / (pAll + DILIGENCE_D));
        scoreSum += diligence;
        dims.add(new AbilityDimensionDto(DILIGENCE.key(), DILIGENCE.label(),
                diligence, entries.size(),
                entries.stream().mapToInt(BookEntry::getPracticeCount).sum(), round2(pAll)));
        double overall = Math.round(scoreSum * 10.0 / dims.size()) / 10.0;
        return new AbilityCore(entries.size(), overall, dims);
    }

    private AbilityModelDto compute(List<BookEntry> entries, String subject) {
        AbilityCore core = computeCore(entries);
        return new AbilityModelDto(subject, core.sampleSize(), core.overall(), core.dimensions());
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    static {
        Set<String> keys = Set.of("CAREFULNESS", "COMPREHENSION", "CONCEPT", "STANDARD", "DILIGENCE");
        if (keys.size() != 5) {
            throw new IllegalStateException("能力模型必须为五维");
        }
    }
}
