package com.yingying.cuotiku.server.service;

import com.yingying.cuotiku.server.ai.ImageUtil;
import com.yingying.cuotiku.server.ai.TencentOcrClient;
import com.yingying.cuotiku.server.dto.AiDto.CropEnhanceRequest;
import com.yingying.cuotiku.server.dto.AiDto.CropEnhanceResponse;
import com.yingying.cuotiku.server.dto.AiDto.PaperProcessRequest;
import com.yingying.cuotiku.server.dto.AiDto.PaperProcessResponse;
import com.yingying.cuotiku.server.dto.AiDto.PaperProcessStepDto;
import com.yingying.cuotiku.server.dto.AiDto.SplitQuestionBoxDto;
import com.yingying.cuotiku.server.dto.AiDto.SplitQuestionsRequest;
import com.yingying.cuotiku.server.dto.AiDto.SplitQuestionsResponse;
import com.yingying.cuotiku.server.dto.AiDto.EraseRequest;
import com.yingying.cuotiku.server.dto.AiDto.EraseResponse;
import com.yingying.cuotiku.server.entity.AiCallLog;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.repository.AiCallLogRepository;
import com.yingying.cuotiku.server.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AiEraseService {

    private static final Logger log = LoggerFactory.getLogger(AiEraseService.class);

    private static final Set<Integer> ENHANCE_TYPES = Set.of(-1, 1, 2, 3, 4, 5, 6);
    private static final int MAX_BASE64_LENGTH = 10_000_000;

    private final TencentOcrClient client;
    private final AiCallLogRepository callLogRepository;

    public AiEraseService(TencentOcrClient client, AiCallLogRepository callLogRepository) {
        this.client = client;
        this.callLogRepository = callLogRepository;
    }

    private void saveCallLog(User user, String aiType, boolean success, Integer errorCode, String errorMessage,
                             String traceId, String requestId, long inputBytes, long outputBytes, long startMillis) {
        try {
            AiCallLog entity = new AiCallLog();
            entity.setUserId(user.getId());
            entity.setPhone(user.getPhone());
            entity.setAiType(aiType);
            entity.setSuccess(success);
            entity.setErrorCode(errorCode);
            entity.setErrorMessage(errorMessage == null ? null
                    : errorMessage.substring(0, Math.min(errorMessage.length(), 500)));
            entity.setTraceId(traceId);
            entity.setRequestId(requestId);
            entity.setInputBytes(inputBytes);
            entity.setOutputBytes(outputBytes);
            entity.setDurationMs(System.currentTimeMillis() - startMillis);
            callLogRepository.save(entity);
        } catch (RuntimeException e) {
            log.warn("[AI统计] 调用流水写入失败 traceId={}: {}", traceId, e.getMessage());
        }
    }

    public EraseResponse erase(EraseRequest request, User user) {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        if (log.isDebugEnabled()) {
            log.debug("[AI擦除] 收到请求 traceId={} phone={} aiEnabled={} base64长度={}",
                    traceId, user.getPhone(), user.isAiEnabled(), request.imageBase64().length());
        }
        if (!user.isAiEnabled()) {
            log.debug("[AI擦除] traceId={} 拒绝：未开通AI权限 phone={}", traceId, user.getPhone());
            throw ApiException.forbidden("当前账号未开通AI去手写权限，请联系管理员");
        }
        byte[] raw;
        try {
            raw = ImageUtil.autoOrient(Base64.getDecoder().decode(normalize(request.imageBase64())));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("图片Base64内容无效");
        }
        String imageBase64 = Base64.getEncoder().encodeToString(raw);
        if (raw.length > 9 * 1024 * 1024) {
            log.debug("[AI擦除] traceId={} 拒绝：图片超限 bytes={}", traceId, raw.length);
            throw ApiException.badRequest("图片超过9MB限制，请减少本次错题数量");
        }
        log.info("AI擦除请求 traceId={} phone={} 图片字节={}", traceId, user.getPhone(), raw.length);
        long startMillis = System.currentTimeMillis();
        TencentOcrClient.EraseResult result;
        try {
            result = client.erase(imageBase64);
        } catch (ApiException e) {
            log.warn("AI擦除失败 traceId={} phone={} code={} message={}",
                    traceId, user.getPhone(), e.getCode(), e.getMessage());
            saveCallLog(user, "ERASE", false, e.getCode(), e.getMessage(),
                    traceId, null, raw.length, 0, startMillis);
            throw e;
        }
        log.info("AI擦除成功 traceId={} phone={} 腾讯云RequestId={}", traceId, user.getPhone(), result.requestId());
        String resultBase64 = normalize(result.imageBase64());
        saveCallLog(user, "ERASE", true, null, null, traceId, result.requestId(),
                raw.length, resultBase64.length() * 3L / 4, startMillis);
        return new EraseResponse(resultBase64, result.requestId(), traceId);
    }

    public CropEnhanceResponse cropEnhance(CropEnhanceRequest request, User user) {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        if (log.isDebugEnabled()) {
            log.debug("[切边增强] 收到请求 traceId={} phone={} aiEnabled={} base64长度={}",
                    traceId, user.getPhone(), user.isAiEnabled(), request.imageBase64().length());
        }
        if (!user.isAiEnabled()) {
            log.debug("[切边增强] traceId={} 拒绝：未开通AI权限 phone={}", traceId, user.getPhone());
            throw ApiException.forbidden("当前账号未开通AI权限，请联系管理员");
        }
        String imageBase64 = orientBase64(normalize(request.imageBase64()));
        if (imageBase64.length() > MAX_BASE64_LENGTH) {
            throw ApiException.badRequest("图片Base64超过腾讯云10M限制，请压缩后重试");
        }
        int enhanceType = request.enhanceType() == null ? -1 : request.enhanceType();
        if (!ENHANCE_TYPES.contains(enhanceType)) {
            throw ApiException.badRequest("增强类型取值：-1不增强 / 1增亮 / 2增强并锐化 / 3黑白 / 4灰度 / 5去阴影增强 / 6点阵图");
        }
        boolean crop = request.crop() == null || request.crop();
        boolean deskew = request.deskew() == null || request.deskew();
        boolean adjustOrientation = Boolean.TRUE.equals(request.adjustOrientation());
        boolean onlyPosition = Boolean.TRUE.equals(request.onlyPosition());
        log.info("AI切边增强请求 traceId={} phone={} base64={}bytes enhanceType={} crop={} deskew={} onlyPosition={}",
                traceId, user.getPhone(), imageBase64.length(), enhanceType, crop, deskew, onlyPosition);

        long inputBytes = imageBase64.length() * 3L / 4;
        long startMillis = System.currentTimeMillis();
        TencentOcrClient.CropEnhanceResult result;
        try {
            result = client.cropEnhance(imageBase64, crop, deskew, adjustOrientation, onlyPosition, enhanceType);
        } catch (ApiException e) {
            log.warn("[切边增强] 失败 traceId={} phone={} code={} message={}",
                    traceId, user.getPhone(), e.getCode(), e.getMessage());
            saveCallLog(user, "CROP_ENHANCE", false, e.getCode(), e.getMessage(),
                    traceId, null, inputBytes, 0, startMillis);
            throw e;
        }
        log.info("AI切边增强成功 traceId={} phone={} 腾讯云RequestId={} 图片={}bytes",
                traceId, user.getPhone(), result.requestId(),
                result.imageBytes() == null ? 0 : result.imageBytes().length);
        saveCallLog(user, "CROP_ENHANCE", true, null, null, traceId, result.requestId(),
                inputBytes, result.imageBytes() == null ? 0 : result.imageBytes().length, startMillis);
        return new CropEnhanceResponse(
                result.imageBytes() == null ? null : Base64.getEncoder().encodeToString(result.imageBytes()),
                result.width(),
                result.height(),
                result.position(),
                result.angle(),
                result.requestId(),
                traceId);
    }

    /**
     * 试卷处理（三合一）：切边增强 → 去手写 → 切题检测，一次调用完成。
     * 切边增强失败时回退原图继续；去手写或切题上游失败则整体失败。
     * 三个步骤分别记入 ai_call_log（CROP_ENHANCE / ERASE / SPLIT_QUESTIONS），共享同一 traceId。
     */
    public PaperProcessResponse paperProcess(PaperProcessRequest request, User user) {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        if (log.isDebugEnabled()) {
            log.debug("[试卷处理] 收到请求 traceId={} phone={} aiEnabled={} base64长度={}",
                    traceId, user.getPhone(), user.isAiEnabled(), request.imageBase64().length());
        }
        if (!user.isAiEnabled()) {
            throw ApiException.forbidden("当前账号未开通AI权限，请联系管理员");
        }
        String originalBase64 = orientBase64(normalize(request.imageBase64()));
        if (originalBase64.length() > MAX_BASE64_LENGTH) {
            throw ApiException.badRequest("图片Base64超过腾讯云10M限制，请压缩后重试");
        }
        boolean wantEnhance = request.enhance() == null || request.enhance();
        boolean wantErase = request.erase() == null || request.erase();
        boolean wantSplit = request.split() == null || request.split();

        // 步骤1：切边增强（失败回退原图）
        String workBase64 = originalBase64;
        PaperProcessStepDto enhanceStep = new PaperProcessStepDto(false, true, null, null, null);
        if (wantEnhance) {
            long start = System.currentTimeMillis();
            try {
                TencentOcrClient.CropEnhanceResult enhanced =
                        client.cropEnhance(originalBase64, true, true, false, false, -1);
                if (enhanced.imageBytes() != null && enhanced.imageBytes().length > 0) {
                    workBase64 = Base64.getEncoder().encodeToString(enhanced.imageBytes());
                    enhanceStep = new PaperProcessStepDto(true, true, enhanced.requestId(), null, null);
                    saveCallLog(user, "CROP_ENHANCE", true, null, null, traceId, enhanced.requestId(),
                            originalBase64.length() * 3L / 4, enhanced.imageBytes().length, start);
                } else {
                    enhanceStep = new PaperProcessStepDto(true, false, enhanced.requestId(), 502, "切边增强未返回图片，已回退原图");
                    saveCallLog(user, "CROP_ENHANCE", false, 502, "切边增强未返回图片，已回退原图",
                            traceId, enhanced.requestId(), originalBase64.length() * 3L / 4, 0, start);
                }
            } catch (ApiException e) {
                log.warn("[试卷处理] 切边增强失败，回退原图 traceId={} code={}", traceId, e.getCode());
                enhanceStep = new PaperProcessStepDto(true, false, null, e.getCode(), e.getMessage());
                saveCallLog(user, "CROP_ENHANCE", false, e.getCode(), e.getMessage(),
                        traceId, null, originalBase64.length() * 3L / 4, 0, start);
            }
        }

        // 步骤2：切题检测（基于增强后/原图，坐标归一化不受后续尺寸影响）
        List<SplitQuestionBoxDto> questions = List.of();
        PaperProcessStepDto splitStep = new PaperProcessStepDto(false, true, null, null, null);
        if (wantSplit) {
            long start = System.currentTimeMillis();
            try {
                TencentOcrClient.SplitResult splitResult = client.splitQuestions(workBase64, true);
                questions = normalizeBoxes(new TencentOcrClient.SplitResult(
                        splitResult.width(), splitResult.height(), splitResult.boxes(), splitResult.requestId()));
                splitStep = new PaperProcessStepDto(true, true, splitResult.requestId(), null, null);
                saveCallLog(user, "SPLIT_QUESTIONS", true, null, null, traceId, splitResult.requestId(),
                        workBase64.length() * 3L / 4, 0, start);
            } catch (ApiException e) {
                saveCallLog(user, "SPLIT_QUESTIONS", false, e.getCode(), e.getMessage(),
                        traceId, null, workBase64.length() * 3L / 4, 0, start);
                throw e;
            }
        }

        // 步骤3：去手写（整页一次擦除）
        String finalBase64 = workBase64;
        String imageKind = wantEnhance && enhanceStep.success() && enhanceStep.applied() ? "enhanced" : "original";
        PaperProcessStepDto eraseStep = new PaperProcessStepDto(false, true, null, null, null);
        if (wantErase) {
            long start = System.currentTimeMillis();
            try {
                TencentOcrClient.EraseResult erased = client.erase(workBase64);
                finalBase64 = normalize(erased.imageBase64());
                imageKind = "erased";
                eraseStep = new PaperProcessStepDto(true, true, erased.requestId(), null, null);
                saveCallLog(user, "ERASE", true, null, null, traceId, erased.requestId(),
                        workBase64.length() * 3L / 4, finalBase64.length() * 3L / 4, start);
            } catch (ApiException e) {
                saveCallLog(user, "ERASE", false, e.getCode(), e.getMessage(),
                        traceId, null, workBase64.length() * 3L / 4, 0, start);
                throw e;
            }
        }

        int[] dims = readDimensions(finalBase64);
        log.info("试卷处理完成 traceId={} phone={} kind={} {}x{} 题框={} enhance={} erase={} split={}",
                traceId, user.getPhone(), imageKind, dims[0], dims[1], questions.size(),
                enhanceStep.success(), eraseStep.success(), splitStep.success());
        return new PaperProcessResponse(traceId, finalBase64, imageKind, dims[0], dims[1],
                questions, enhanceStep, eraseStep, splitStep);
    }

    private int[] readDimensions(String base64) {
        try {
            java.awt.image.BufferedImage image =
                    javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(base64)));
            if (image != null) {
                return new int[]{image.getWidth(), image.getHeight()};
            }
        } catch (Exception e) {
            log.warn("[试卷处理] 读取成品图尺寸失败: {}", e.getMessage());
        }
        return new int[]{0, 0};
    }

    public SplitQuestionsResponse splitQuestions(SplitQuestionsRequest request, User user) {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        if (log.isDebugEnabled()) {
            log.debug("[切题检测] 收到请求 traceId={} phone={} aiEnabled={} base64长度={}",
                    traceId, user.getPhone(), user.isAiEnabled(), request.imageBase64().length());
        }
        if (!user.isAiEnabled()) {
            throw ApiException.forbidden("当前账号未开通AI权限，请联系管理员");
        }
        String imageBase64 = orientBase64(normalize(request.imageBase64()));
        if (imageBase64.length() > MAX_BASE64_LENGTH) {
            throw ApiException.badRequest("图片Base64超过腾讯云10M限制，请压缩后重试");
        }
        boolean useNewModel = request.useNewModel() == null || request.useNewModel();
        long inputBytes = imageBase64.length() * 3L / 4;
        long startMillis = System.currentTimeMillis();
        TencentOcrClient.SplitResult result;
        try {
            result = client.splitQuestions(imageBase64, useNewModel);
        } catch (ApiException e) {
            log.warn("[切题检测] 失败 traceId={} phone={} code={} message={}",
                    traceId, user.getPhone(), e.getCode(), e.getMessage());
            saveCallLog(user, "SPLIT_QUESTIONS", false, e.getCode(), e.getMessage(),
                    traceId, null, inputBytes, 0, startMillis);
            throw e;
        }
        List<SplitQuestionBoxDto> questions = normalizeBoxes(result);
        log.info("切题检测成功 traceId={} phone={} 腾讯云RequestId={} {}x{} 题框={}个",
                traceId, user.getPhone(), result.requestId(),
                result.width(), result.height(), questions.size());
        saveCallLog(user, "SPLIT_QUESTIONS", true, null, null, traceId, result.requestId(),
                inputBytes, 0, startMillis);
        return new SplitQuestionsResponse(result.width(), result.height(), questions,
                result.requestId(), traceId);
    }

    private List<SplitQuestionBoxDto> normalizeBoxes(TencentOcrClient.SplitResult result) {
        int w = result.width();
        int h = result.height();
        if (w <= 0 || h <= 0) {
            return List.of();
        }
        double minSide = 40;
        double minArea = 0.005 * w * h;
        List<double[]> kept = new ArrayList<>();
        for (TencentOcrClient.RawBox box : result.boxes()) {
            double minX = clamp(box.minX(), 0, w);
            double maxX = clamp(box.maxX(), 0, w);
            double minY = clamp(box.minY(), 0, h);
            double maxY = clamp(box.maxY(), 0, h);
            double bw = maxX - minX;
            double bh = maxY - minY;
            if (bw < minSide || bh < minSide || bw * bh < minArea) {
                continue;
            }
            kept.add(new double[]{minX, minY, maxX, maxY});
        }
        kept.sort(Comparator.comparingDouble((double[] b) -> -(b[2] - b[0]) * (b[3] - b[1])));
        List<double[]> deduped = new ArrayList<>();
        for (double[] box : kept) {
            boolean redundant = false;
            for (double[] other : deduped) {
                if (iou(box, other) > 0.9 || containment(box, other) > 0.9) {
                    redundant = true;
                    break;
                }
            }
            if (!redundant) {
                deduped.add(box);
            }
        }
        double band = Math.max(40, h * 0.04);
        deduped.sort(Comparator.comparingInt((double[] b) -> (int) (b[1] / band))
                .thenComparingDouble(b -> b[0]));
        List<SplitQuestionBoxDto> questions = new ArrayList<>();
        int index = 1;
        for (double[] b : deduped) {
            int x = (int) Math.round(b[0]);
            int y = (int) Math.round(b[1]);
            int bw = (int) Math.round(b[2] - b[0]);
            int bh = (int) Math.round(b[3] - b[1]);
            questions.add(new SplitQuestionBoxDto(index++, x, y, bw, bh,
                    round4(b[0] / w), round4(b[1] / h), round4(bw / (double) w), round4(bh / (double) h)));
        }
        return questions;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static double iou(double[] a, double[] b) {
        double ix = Math.max(0, Math.min(a[2], b[2]) - Math.max(a[0], b[0]));
        double iy = Math.max(0, Math.min(a[3], b[3]) - Math.max(a[1], b[1]));
        double inter = ix * iy;
        double areaA = (a[2] - a[0]) * (a[3] - a[1]);
        double areaB = (b[2] - b[0]) * (b[3] - b[1]);
        return inter <= 0 ? 0 : inter / (areaA + areaB - inter);
    }

    private static double containment(double[] small, double[] big) {
        double ix = Math.max(0, Math.min(small[2], big[2]) - Math.max(small[0], big[0]));
        double iy = Math.max(0, Math.min(small[3], big[3]) - Math.max(small[1], big[1]));
        double inter = ix * iy;
        double areaSmall = (small[2] - small[0]) * (small[3] - small[1]);
        return areaSmall <= 0 ? 0 : inter / areaSmall;
    }

    private String orientBase64(String base64) {
        try {
            byte[] oriented = ImageUtil.autoOrient(Base64.getDecoder().decode(base64));
            return Base64.getEncoder().encodeToString(oriented);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("图片Base64内容无效");
        }
    }

    private String normalize(String value) {
        String trimmed = value.trim();
        int comma = trimmed.indexOf(',');
        if (trimmed.startsWith("data:") && comma > 0) {
            return trimmed.substring(comma + 1);
        }
        return trimmed;
    }
}
