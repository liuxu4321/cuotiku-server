package com.yingying.cuotiku.server.service;

import com.yingying.cuotiku.server.dto.AdminUserDto.PageResponse;
import com.yingying.cuotiku.server.dto.BookDto.*;
import com.yingying.cuotiku.server.dto.BookDto.PracticeHistoryDto;
import com.yingying.cuotiku.server.dto.BookDto.PracticeRecordDto;
import com.yingying.cuotiku.server.dto.BookDto.PracticeRecordRequest;
import com.yingying.cuotiku.server.entity.BookEntry;
import com.yingying.cuotiku.server.entity.BookPracticeRecord;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.repository.BookEntryRepository;
import com.yingying.cuotiku.server.repository.BookPracticeRecordRepository;
import com.yingying.cuotiku.server.storage.BookStorage;
import com.yingying.cuotiku.server.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class BookService {

    private static final Logger log = LoggerFactory.getLogger(BookService.class);
    private static final Set<String> SUBJECTS = Set.of("语文", "数学", "英语");
    private static final Set<String> ERROR_TYPES = Set.of("马虎", "不会", "概念不清", "其他");
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter KEY_MONTH =
            DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneId.systemDefault());
    private static final int THUMB_MAX = 480;

    private final BookEntryRepository repository;
    private final BookStorage storage;
    private final BookPracticeRecordRepository practiceRepository;

    public BookService(BookEntryRepository repository, BookStorage storage,
                       BookPracticeRecordRepository practiceRepository) {
        this.repository = repository;
        this.storage = storage;
        this.practiceRepository = practiceRepository;
    }

    public record ImagePayload(byte[] bytes, String contentType) {}

    @Transactional
    public EntryDto addEntry(AddEntryRequest request, User user) {
        if (log.isDebugEnabled()) {
            log.debug("[错题] 添加开始 phone={} grade={} term={} subject={} errorType={} base64长度={}",
                    user.getPhone(), request.grade(), request.term(), request.subject(), request.errorType(),
                    request.imageBase64().length());
        }
        if (!SUBJECTS.contains(request.subject())) {
            throw ApiException.badRequest("科目只能是：" + String.join("、", SUBJECTS));
        }
        String errorType = request.errorType() == null || request.errorType().isBlank()
                ? "其他" : request.errorType().trim();
        if (!ERROR_TYPES.contains(errorType)) {
            throw ApiException.badRequest("错误类型只能是：" + String.join("、", ERROR_TYPES));
        }
        byte[] imageBytes = com.yingying.cuotiku.server.ai.ImageUtil.autoOrient(decodeBase64(request.imageBase64()));
        ImageMeta meta = readImageMeta(imageBytes);

        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String key = "book/%d/%s/%s.zip".formatted(user.getId(), KEY_MONTH.format(now), id);
        if (log.isDebugEnabled()) {
            log.debug("[错题] 图片解析 id={} format={} {}x{} bytes={}",
                    id, meta.format(), meta.width(), meta.height(), imageBytes.length);
        }

        byte[] thumb = renderThumb(imageBytes);
        byte[] zip = buildZip(id, meta.format(), imageBytes, thumb);
        if (log.isDebugEnabled()) {
            log.debug("[错题] 打包完成 id={} 缩略图={}bytes zip={}bytes(为原图{}%) key={}",
                    id, thumb == null ? 0 : thumb.length, zip.length,
                    imageBytes.length == 0 ? 0 : Math.round(zip.length * 100.0 / imageBytes.length), key);
        }
        try {
            storage.put(key, zip);
        } catch (RuntimeException e) {
            log.warn("[错题] 存储上传失败，回滚 id={} key={}: {}", id, key, e.getMessage());
            storage.delete(key);
            throw e;
        }

        BookEntry entry = new BookEntry();
        entry.setId(id);
        entry.setUserId(user.getId());
        entry.setGrade(request.grade());
        entry.setTerm(request.term());
        entry.setSubject(request.subject());
        entry.setErrorType(errorType);
        entry.setCreatedAt(now);
        entry.setWidth(meta.width());
        entry.setHeight(meta.height());
        entry.setPracticeCount(0);
        entry.setObjectKey(key);
        entry.setFormat(meta.format());
        entry.setHasThumb(thumb != null);
        entry.setSizeBytes(imageBytes.length);
        entry.setRemark(blankToNull(request.remark()));
        entry.setAnswer(blankToNull(request.answer()));
        try {
            repository.save(entry);
        } catch (RuntimeException e) {
            log.warn("[错题] 元数据入库失败，清理存储对象 id={} key={}: {}", id, key, e.getMessage());
            storage.delete(key);
            throw e;
        }
        log.debug("[错题] 添加完成 id={} phone={} key={}", id, user.getPhone(), key);
        return toDto(entry);
    }

    @Transactional(readOnly = true)
    public PageResponse<EntryDto> list(Long userId, Integer grade, Integer term, String subject,
                                       String errorType, int page, int size) {
        if (subject != null && !subject.isBlank() && !SUBJECTS.contains(subject)) {
            throw ApiException.badRequest("科目只能是：" + String.join("、", SUBJECTS));
        }
        if (errorType != null && !errorType.isBlank() && !ERROR_TYPES.contains(errorType)) {
            throw ApiException.badRequest("错误类型只能是：" + String.join("、", ERROR_TYPES));
        }
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        Page<BookEntry> result = repository.search(userId,
                grade,
                term,
                subject == null || subject.isBlank() ? null : subject,
                errorType == null || errorType.isBlank() ? null : errorType,
                pageable);
        List<EntryDto> items = toDtos(result.getContent());
        if (log.isDebugEnabled()) {
            log.debug("[错题] 列表查询 userId={} grade={} term={} subject={} errorType={} page={} size={} → total={} 本页={}",
                    userId, grade, term, subject, errorType, page, size, result.getTotalElements(), items.size());
        }
        return new PageResponse<>(items, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public EntryDto get(Long userId, String id) {
        log.debug("[错题] 单条查询 userId={} id={}", userId, id);
        return toDto(require(userId, id));
    }

    @Transactional
    public PracticeRecordDto addPractice(Long userId, String entryId, PracticeRecordRequest request) {
        BookEntry entry = require(userId, entryId);
        Instant at = request.practicedAt() == null
                ? Instant.now()
                : Instant.ofEpochMilli(Math.min(request.practicedAt(), System.currentTimeMillis()));
        BookPracticeRecord record = new BookPracticeRecord();
        record.setEntryId(entry.getId());
        record.setUserId(userId);
        record.setPracticedAt(at);
        record.setAnswerContent(request.answerContent() == null || request.answerContent().isBlank()
                ? null : request.answerContent().trim());
        record.setCorrect(Boolean.TRUE.equals(request.correct()));
        practiceRepository.save(record);
        entry.setPracticeCount(entry.getPracticeCount() + 1);
        repository.save(entry);
        log.debug("[刷题] 记录入库 entryId={} correct={} 累计practiceCount={}",
                entryId, record.isCorrect(), entry.getPracticeCount());
        return toRecordDto(record);
    }

    @Transactional(readOnly = true)
    public PracticeHistoryDto listPractices(Long userId, String entryId, int page, int size) {
        require(userId, entryId);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200),
                Sort.by(Sort.Direction.DESC, "practicedAt", "id"));
        Page<BookPracticeRecord> result = practiceRepository.findByEntryIdAndUserId(entryId, userId, pageable);
        BookPracticeRecordRepository.EntryStat stat = statMap(List.of(entryId)).get(entryId);
        long total = stat == null ? 0 : stat.getTotal();
        long correct = stat == null ? 0 : stat.getCorrect();
        return new PracticeHistoryDto(
                result.getContent().stream().map(BookService::toRecordDto).toList(),
                result.getTotalElements(),
                result.getNumber(),
                result.getSize(),
                total,
                correct,
                total == 0 ? null : Math.round(correct * 1000.0 / total) / 10.0,
                stat == null || stat.getLastAt() == null ? null : stat.getLastAt().toEpochMilli());
    }

    @Transactional(readOnly = true)
    public PracticeHistoryDto history(Long userId, Boolean correct, String start, String end,
                                      int page, int size) {
        Instant startAt = parseDateInstant(start, false);
        Instant endAt = parseDateInstant(end, true);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200),
                Sort.by(Sort.Direction.DESC, "practicedAt", "id"));
        Page<BookPracticeRecord> result =
                practiceRepository.searchHistory(userId, correct, startAt, endAt, pageable);
        long allCount = practiceRepository.countByUserId(userId);
        long correctCount = practiceRepository.countByUserIdAndCorrectTrue(userId);
        Instant lastAt = practiceRepository.lastPracticedAt(userId);
        return new PracticeHistoryDto(
                result.getContent().stream().map(BookService::toRecordDto).toList(),
                result.getTotalElements(),
                result.getNumber(),
                result.getSize(),
                allCount,
                correctCount,
                allCount == 0 ? null : Math.round(correctCount * 1000.0 / allCount) / 10.0,
                lastAt == null ? null : lastAt.toEpochMilli());
    }

    private static PracticeRecordDto toRecordDto(BookPracticeRecord record) {
        return new PracticeRecordDto(
                record.getId(),
                record.getEntryId(),
                record.getPracticedAt().toEpochMilli(),
                TIME_FORMAT.format(record.getPracticedAt()),
                record.getAnswerContent(),
                record.isCorrect());
    }

    private static Instant parseDateInstant(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(value.trim());
            return endOfDay
                    ? date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1)
                    : date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            throw ApiException.badRequest("日期格式不正确，应为 yyyy-MM-dd");
        }
    }

    @Transactional(readOnly = true)
    public List<ImageItem> getImages(Long userId, List<String> ids, String kind) {
        if (log.isDebugEnabled()) {
            log.debug("[错题] 批量取图开始 userId={} kind={} 请求={}条", userId, kind, ids.size());
        }
        Map<String, BookEntry> owned = new HashMap<>();
        for (BookEntry entry : repository.findByIdInAndUserId(ids, userId)) {
            owned.put(entry.getId(), entry);
        }
        log.debug("[错题] 批量取图 命中={}条", owned.size());
        List<ImageItem> result = new ArrayList<>();
        for (String id : ids) {
            BookEntry entry = owned.get(id);
            if (entry == null) {
                continue;
            }
            try {
                ImagePayload payload = readImage(entry, kind);
                result.add(new ImageItem(id, payload.contentType(),
                        Base64.getEncoder().encodeToString(payload.bytes())));
            } catch (ApiException e) {
                log.warn("批量取图跳过 id={}: {}", id, e.getMessage());
            }
        }
        log.debug("[错题] 批量取图完成 返回={}条", result.size());
        return result;
    }

    @Transactional(readOnly = true)
    public ImagePayload getImage(Long userId, String id, String kind) {
        BookEntry entry = require(userId, id);
        return readImage(entry, kind);
    }

    private ImagePayload readImage(BookEntry entry, String kind) {
        log.debug("[错题] 读取图片 id={} kind={} key={}", entry.getId(), kind, entry.getObjectKey());
        byte[] zip = storage.get(entry.getObjectKey());
        boolean wantThumb = "thumb".equalsIgnoreCase(kind);
        byte[] bytes = null;
        byte[] original = null;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry ze;
            while ((ze = zin.getNextEntry()) != null) {
                byte[] content = zin.readAllBytes();
                if (ze.getName().equals("thumb.jpg")) {
                    bytes = content;
                } else if (ze.getName().startsWith("image.")) {
                    original = content;
                }
            }
        } catch (IOException e) {
            throw new ApiException(500, "错题压缩包解析失败");
        }
        if (wantThumb && bytes != null) {
            log.debug("[错题] 返回缩略图 id={} bytes={}", entry.getId(), bytes.length);
            return new ImagePayload(bytes, "image/jpeg");
        }
        if (wantThumb) {
            log.debug("[错题] 无缩略图，回退原图 id={}", entry.getId());
        }
        if (original == null) {
            throw new ApiException(500, "错题压缩包缺少原图");
        }
        log.debug("[错题] 返回原图 id={} format={} bytes={}", entry.getId(), entry.getFormat(), original.length);
        return new ImagePayload(original, "image/" + entry.getFormat());
    }

    @Transactional
    public EntryDto update(Long userId, String id, UpdateEntryRequest request) {
        BookEntry entry = require(userId, id);
        if (request.grade() != null) {
            entry.setGrade(request.grade());
        }
        if (request.term() != null) {
            entry.setTerm(request.term());
        }
        if (request.subject() != null && !request.subject().isBlank()) {
            if (!SUBJECTS.contains(request.subject())) {
                throw ApiException.badRequest("科目只能是：" + String.join("、", SUBJECTS));
            }
            entry.setSubject(request.subject());
        }
        if (request.errorType() != null && !request.errorType().isBlank()) {
            if (!ERROR_TYPES.contains(request.errorType())) {
                throw ApiException.badRequest("错误类型只能是：" + String.join("、", ERROR_TYPES));
            }
            entry.setErrorType(request.errorType());
        }
        if (request.remark() != null) {
            entry.setRemark(blankToNull(request.remark()));
        }
        if (request.answer() != null) {
            entry.setAnswer(blankToNull(request.answer()));
        }
        repository.save(entry);
        if (log.isDebugEnabled()) {
            log.debug("[错题] 元数据更新 id={} grade={} term={} subject={} errorType={}",
                    entry.getId(), entry.getGrade(), entry.getTerm(), entry.getSubject(), entry.getErrorType());
        }
        return toDto(entry);
    }

    private static final List<String> ERROR_TYPE_ORDER = List.of("马虎", "不会", "概念不清", "其他");

    /**
     * 公平随机组卷：最少练习分层轮转。
     * 按 practiceCount 升序分层，同层内随机抽取；保证所有错题都被练到 k 次之前，
     * 不会有错题被练到 k+1 次，实现全覆盖轮转。
     * 本方法不增加刷题计数，客户端应在打印成功后调用 practice 接口 +1。
     */
    @Transactional(readOnly = true)
    public RandomPaperResponse randomPaper(Long userId, RandomPaperRequest request) {
        if (request.subject() != null && !request.subject().isBlank()
                && !SUBJECTS.contains(request.subject())) {
            throw ApiException.badRequest("科目只能是：" + String.join("、", SUBJECTS));
        }
        int totalRequested = 0;
        for (Map.Entry<String, Integer> e : request.counts().entrySet()) {
            if (!ERROR_TYPES.contains(e.getKey())) {
                throw ApiException.badRequest("错误类型只能是：" + String.join("、", ERROR_TYPES));
            }
            if (e.getValue() == null || e.getValue() < 0 || e.getValue() > 100) {
                throw ApiException.badRequest("单类型抽取数量须在0-100之间");
            }
            totalRequested += e.getValue();
        }
        if (totalRequested == 0) {
            throw ApiException.badRequest("抽取总数量需大于0");
        }
        if (totalRequested > 200) {
            throw ApiException.badRequest("单次最多抽取200道");
        }
        String subject = request.subject() == null || request.subject().isBlank() ? null : request.subject();

        List<EntryDto> items = new ArrayList<>();
        Map<String, TypeStat> byType = new LinkedHashMap<>();
        int totalSelected = 0;
        for (String type : ERROR_TYPE_ORDER) {
            int need = request.counts().getOrDefault(type, 0);
            List<BookEntry> pool = repository.findForRandom(userId, type, request.grade(), request.term(), subject);
            List<BookEntry> shuffled = new ArrayList<>(pool);
            Collections.shuffle(shuffled);
            // 稳定排序：practiceCount 升序分层，层内保持随机序
            shuffled.sort(Comparator.comparingInt(BookEntry::getPracticeCount));
            List<BookEntry> picked = shuffled.subList(0, Math.min(need, shuffled.size()));
            items.addAll(toDtos(picked));
            totalSelected += picked.size();
            byType.put(type, new TypeStat(need, picked.size(), pool.size()));
            if (log.isDebugEnabled()) {
                log.debug("[组卷] 类型={} 需求={} 题池={} 抽中={} 题池练习次数分布={}",
                        type, need, pool.size(), picked.size(),
                        pool.stream().map(x -> String.valueOf(x.getPracticeCount()))
                                .sorted().reduce((a, b) -> a + "," + b).orElse("空"));
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("[组卷] 公平抽题完成 userId={} grade={} term={} subject={} 请求={} 抽中={}",
                    userId, request.grade(), request.term(), subject, totalRequested, totalSelected);
        }
        return new RandomPaperResponse(items, totalRequested, totalSelected, byType);
    }

    @Transactional
    public int bumpPractice(Long userId, List<String> ids) {
        Set<String> owned = new HashSet<>();
        for (BookEntry entry : repository.findByIdInAndUserId(ids, userId)) {
            owned.add(entry.getId());
        }
        if (owned.isEmpty()) {
            log.debug("[错题] 刷题计数 无命中 userId={} 请求={}条", userId, ids.size());
            return 0;
        }
        int updated = repository.bumpPracticeCount(owned, userId);
        log.debug("[错题] 刷题计数 userId={} 请求={}条 命中={}条 更新={}", userId, ids.size(), owned.size(), updated);
        return updated;
    }

    @Transactional
    public void delete(Long userId, String id) {
        BookEntry entry = require(userId, id);
        int records = practiceRepository.deleteByEntryId(entry.getId());
        repository.delete(entry);
        storage.delete(entry.getObjectKey());
        log.debug("[错题] 删除完成 id={} userId={} key={} 刷题记录={}条",
                id, userId, entry.getObjectKey(), records);
    }

    private BookEntry require(Long userId, String id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("错题不存在：" + id));
    }

    private EntryDto toDto(BookEntry entry) {
        return toDto(entry, statMap(List.of(entry.getId())).get(entry.getId()));
    }

    private static EntryDto toDto(BookEntry entry, BookPracticeRecordRepository.EntryStat stat) {
        long total = stat == null ? 0 : stat.getTotal();
        long correct = stat == null ? 0 : stat.getCorrect();
        Double accuracy = total == 0 ? null : Math.round(correct * 1000.0 / total) / 10.0;
        Long lastAt = stat == null || stat.getLastAt() == null ? null : stat.getLastAt().toEpochMilli();
        return new EntryDto(
                entry.getId(),
                entry.getGrade(),
                entry.getTerm(),
                entry.getSubject(),
                entry.getErrorType(),
                entry.getCreatedAt().toEpochMilli(),
                TIME_FORMAT.format(entry.getCreatedAt()),
                entry.getWidth(),
                entry.getHeight(),
                entry.getPracticeCount(),
                entry.getRemark(),
                entry.getAnswer(),
                "/api/book/entries/" + entry.getId() + "/image",
                "/api/book/entries/" + entry.getId() + "/image?kind=thumb",
                total,
                correct,
                accuracy,
                lastAt);
    }

    public Map<String, BookPracticeRecordRepository.EntryStat> statMap(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<String, BookPracticeRecordRepository.EntryStat> map = new HashMap<>();
        for (BookPracticeRecordRepository.EntryStat stat : practiceRepository.statsByEntries(ids)) {
            map.put(stat.getEntryId(), stat);
        }
        return map;
    }

    private List<EntryDto> toDtos(List<BookEntry> entries) {
        Map<String, BookPracticeRecordRepository.EntryStat> stats =
                statMap(entries.stream().map(BookEntry::getId).toList());
        return entries.stream().map(e -> toDto(e, stats.get(e.getId()))).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record PreparedImage(byte[] bytes, int width, int height, String format, byte[] thumb, byte[] zip) {}

    public PreparedImage prepareImage(String imageBase64) {
        byte[] imageBytes = com.yingying.cuotiku.server.ai.ImageUtil.autoOrient(decodeBase64(imageBase64));
        ImageMeta meta = readImageMeta(imageBytes);
        String id = UUID.randomUUID().toString();
        byte[] thumb = renderThumb(imageBytes);
        byte[] zip = buildZip(id, meta.format(), imageBytes, thumb);
        return new PreparedImage(imageBytes, meta.width(), meta.height(), meta.format(), thumb, zip);
    }

    private byte[] decodeBase64(String value) {
        String trimmed = value.trim();
        int comma = trimmed.indexOf(',');
        if (trimmed.startsWith("data:") && comma > 0) {
            trimmed = trimmed.substring(comma + 1);
        }
        try {
            return Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("图片Base64内容无效");
        }
    }

    private record ImageMeta(int width, int height, String format) {}

    private ImageMeta readImageMeta(byte[] bytes) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw ApiException.badRequest("无法解析图片内容，请上传PNG/JPEG格式图片");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg") && !format.equals("jpg")) {
                    format = "png";
                }
                if (format.equals("jpeg")) {
                    format = "jpg";
                }
                return new ImageMeta(reader.getWidth(0), reader.getHeight(0), format);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw ApiException.badRequest("无法解析图片内容，请上传PNG/JPEG格式图片");
        }
    }

    private byte[] renderThumb(byte[] imageBytes) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (source == null) {
                return null;
            }
            int width = source.getWidth();
            int height = source.getHeight();
            double scale = Math.min(1.0, (double) THUMB_MAX / Math.max(width, height));
            int tw = Math.max(1, (int) Math.round(width * scale));
            int th = Math.max(1, (int) Math.round(height * scale));
            BufferedImage thumb = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumb.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, tw, th);
            g.drawImage(source, 0, 0, tw, th, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(thumb, "jpg", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("缩略图生成失败，仅存储原图: {}", e.getMessage());
            return null;
        }
    }

    private byte[] buildZip(String id, String format, byte[] imageBytes, byte[] thumb) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zout = new ZipOutputStream(out)) {
                zout.putNextEntry(new ZipEntry("image." + format));
                zout.write(imageBytes);
                zout.closeEntry();
                if (thumb != null) {
                    zout.putNextEntry(new ZipEntry("thumb.jpg"));
                    zout.write(thumb);
                    zout.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException(500, "错题压缩包生成失败");
        }
    }
}
