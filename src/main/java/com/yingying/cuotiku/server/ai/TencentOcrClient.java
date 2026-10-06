package com.yingying.cuotiku.server.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingying.cuotiku.server.config.AppProperties;
import com.yingying.cuotiku.server.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class TencentOcrClient {

    private static final Logger log = LoggerFactory.getLogger(TencentOcrClient.class);
    private static final String SERVICE = "ocr";
    private static final String API_VERSION = "2018-11-19";

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public TencentOcrClient(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean configured() {
        return properties.ai().tencent().configured();
    }

    public record EraseResult(String imageBase64, String requestId) {}

    public record RawBox(double minX, double minY, double maxX, double maxY) {}

    public record SplitResult(int width, int height, List<RawBox> boxes, String requestId) {}

    public SplitResult splitQuestions(String imageBase64, boolean useNewModel) {
        Map<String, Object> params = new HashMap<>();
        params.put("ImageBase64", imageBase64);
        params.put("UseNewModel", useNewModel);
        params.put("EnableImageCrop", false);
        JsonNode body = callApi("QuestionSplitLayoutOCR", params);

        List<RawBox> boxes = new ArrayList<>();
        int width = 0;
        int height = 0;
        for (JsonNode info : body.path("QuestionInfo")) {
            width = Math.max(width, info.path("Width").asInt(0));
            height = Math.max(height, info.path("Height").asInt(0));
            for (JsonNode block : info.path("ResultList")) {
                JsonNode subs = useNewModel ? null : block.path("Question");
                if (subs != null && subs.size() > 0) {
                    for (JsonNode sub : subs) {
                        RawBox box = quadToBox(sub.path("Coord"));
                        if (box != null) boxes.add(box);
                    }
                } else {
                    RawBox box = quadToBox(block.path("Coord"));
                    if (box != null) boxes.add(box);
                }
            }
        }
        String requestId = body.path("RequestId").asText(null);
        if (log.isDebugEnabled()) {
            log.debug("[腾讯云OCR] 切题检测成功 RequestId={} {}x{} 题框={}个",
                    requestId, width, height, boxes.size());
        }
        return new SplitResult(width, height, boxes, requestId);
    }

    private RawBox quadToBox(JsonNode coord) {
        if (!coord.isArray() || coord.isEmpty()) {
            return null;
        }
        JsonNode quad = coord.get(0);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (String corner : new String[]{"LeftTop", "RightTop", "RightBottom", "LeftBottom"}) {
            JsonNode point = quad.path(corner);
            minX = Math.min(minX, point.path("X").asDouble());
            maxX = Math.max(maxX, point.path("X").asDouble());
            minY = Math.min(minY, point.path("Y").asDouble());
            maxY = Math.max(maxY, point.path("Y").asDouble());
        }
        if (minX > maxX || minY > maxY) {
            return null;
        }
        return new RawBox(minX, minY, maxX, maxY);
    }

    public record CropEnhanceResult(
            byte[] imageBytes, Integer width, Integer height,
            List<Integer> position, Integer angle, String requestId) {}

    public EraseResult erase(String imageBase64) {
        String normalized = normalizeForErase(imageBase64);
        JsonNode body;
        try {
            body = callApi("EraseHandwrittenImageOCR", Map.of("ImageBase64", normalized));
        } catch (ApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("InternalError")) {
                log.warn("[腾讯云OCR] 擦除遇 InternalError，1s 后重试一次");
                sleep1s();
                body = callApi("EraseHandwrittenImageOCR", Map.of("ImageBase64", normalized));
            } else {
                throw e;
            }
        }
        String image = body.path("Image").asText(null);
        if (image == null || image.isBlank()) {
            throw new ApiException(502, "腾讯云接口成功返回，但没有擦除后的图片");
        }
        String requestId = body.path("RequestId").asText(null);
        if (log.isDebugEnabled()) {
            log.debug("[腾讯云OCR] 擦除成功 RequestId={} 返回图片base64={}bytes", requestId, image.length());
        }
        return new EraseResult(image, requestId);
    }

    public CropEnhanceResult cropEnhance(String imageBase64, boolean crop, boolean deskew,
                                         boolean adjustOrientation, boolean onlyPosition,
                                         int enhanceType) {
        Map<String, Object> params = new HashMap<>();
        params.put("ImageBase64", imageBase64);
        params.put("Crop", crop ? 1 : 0);
        params.put("Deskew", deskew ? 1 : 0);
        params.put("AdjustOrientation", adjustOrientation ? 1 : 0);
        params.put("OnlyPosition", onlyPosition ? 1 : 0);
        params.put("EnhanceType", enhanceType);
        JsonNode body = callApi("CropEnhanceImageOCR", params);

        List<Integer> position = new ArrayList<>();
        for (JsonNode point : body.path("Position")) {
            position.add(point.asInt());
        }
        Integer angle = body.hasNonNull("Angle") ? body.path("Angle").asInt() : null;
        Integer width = body.hasNonNull("CroppedWidth") ? body.path("CroppedWidth").asInt() : null;
        Integer height = body.hasNonNull("CroppedHeight") ? body.path("CroppedHeight").asInt() : null;
        String requestId = body.path("RequestId").asText(null);

        byte[] imageBytes = null;
        if (!onlyPosition) {
            String url = body.path("CroppedImageUrl").asText(null);
            String inline = body.path("CroppedImage").asText(null);
            if (url != null && !url.isBlank()) {
                imageBytes = download(url);
            } else if (inline != null && !inline.isBlank()) {
                imageBytes = Base64.getDecoder().decode(
                        inline.contains(",") ? inline.substring(inline.indexOf(',') + 1) : inline);
            } else {
                throw new ApiException(502, "腾讯云接口成功返回，但没有处理后的图片");
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("[腾讯云OCR] 切边增强成功 RequestId={} {}x{} 图片={}bytes 角点数={} angle={}",
                    requestId, width, height, imageBytes == null ? 0 : imageBytes.length,
                    position.size(), angle);
        }
        return new CropEnhanceResult(imageBytes, width, height,
                position.isEmpty() ? null : position, angle, requestId);
    }

    private JsonNode callApi(String action, Map<String, Object> params) {
        AppProperties.Ai.Tencent config = properties.ai().tencent();
        if (!config.configured()) {
            throw new ApiException(503, "后台尚未配置腾讯云AI凭据，请联系管理员");
        }
        try {
            String payload = objectMapper.writeValueAsString(params);
            HttpRequest request = signedRequest(action, payload, config);
            if (log.isDebugEnabled()) {
                log.debug("[腾讯云OCR] 发起请求 action={} host={} region={} payload={}bytes 超时={}s",
                        action, config.endpoint(), config.region(), payload.length(), config.timeoutSeconds());
            }
            long startMillis = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (log.isDebugEnabled()) {
                log.debug("[腾讯云OCR] 收到响应 action={} status={} 耗时={}ms body={}bytes",
                        action, response.statusCode(), System.currentTimeMillis() - startMillis,
                        response.body().length());
            }
            if (response.statusCode() != 200) {
                log.warn("腾讯云接口 HTTP {}: {}", response.statusCode(), abbreviate(response.body()));
                throw new ApiException(502, "腾讯云接口 HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode body = root.path("Response");
            JsonNode error = body.path("Error");
            if (!error.isMissingNode() && error.has("Code")) {
                String code = error.path("Code").asText();
                String message = error.path("Message").asText();
                String requestId = body.path("RequestId").asText("");
                log.warn("腾讯云错误 {} (RequestId={}): {}", code, requestId, message);
                throw new ApiException(502, "腾讯云错误 " + code
                        + (requestId.isBlank() ? "" : "（RequestId=" + requestId + "）")
                        + "：" + translate(code, message));
            }
            return body;
        } catch (ApiException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ApiException(504, "调用腾讯云超时，请稍后重试");
        } catch (java.io.IOException e) {
            log.error("调用腾讯云失败", e);
            throw new ApiException(502, "无法连接腾讯云，请检查后台网络");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(502, "调用腾讯云被中断");
        } catch (Exception e) {
            log.error("调用腾讯云失败", e);
            throw new ApiException(502, "调用腾讯云失败：" + e.getMessage());
        }
    }

    private static final int MAX_SIDE = 4096;
    private static final long MAX_PIXELS = 12_000_000L;

    private void sleep1s() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 擦除引擎对超大分辨率/带 alpha/非常规色彩模型的图片易返回 InternalError，
     * 调用前归一化：转 RGB、超限时等比缩到 4096 边/1200 万像素内、重编码为标准 JPEG。
     * 无需处理时原样返回，避免额外质量损失。
     */
    private String normalizeForErase(String imageBase64) {
        try {
            byte[] raw = java.util.Base64.getDecoder().decode(imageBase64);
            java.awt.image.BufferedImage source;
            try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(raw)) {
                source = javax.imageio.ImageIO.read(in);
            }
            if (source == null) {
                return imageBase64;
            }
            int type = source.getType();
            boolean standard = type == java.awt.image.BufferedImage.TYPE_INT_RGB
                    || type == java.awt.image.BufferedImage.TYPE_3BYTE_BGR;
            long pixels = (long) source.getWidth() * source.getHeight();
            int maxSide = Math.max(source.getWidth(), source.getHeight());
            if (standard && pixels <= MAX_PIXELS && maxSide <= MAX_SIDE) {
                return imageBase64;
            }
            double scale = Math.min(1.0, Math.min((double) MAX_SIDE / maxSide,
                    Math.sqrt((double) MAX_PIXELS / Math.max(1, pixels))));
            int tw = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int th = Math.max(1, (int) Math.round(source.getHeight() * scale));
            java.awt.image.BufferedImage work = new java.awt.image.BufferedImage(tw, th,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = work.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, tw, th);
            g.drawImage(source, 0, 0, tw, th, null);
            g.dispose();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(work, "jpg", out);
            log.debug("[腾讯云OCR] 擦除前图片归一化 {}x{} type={} → {}x{} jpeg={}bytes",
                    source.getWidth(), source.getHeight(), type, tw, th, out.size());
            return java.util.Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IllegalArgumentException e) {
            return imageBase64;
        } catch (Exception e) {
            log.warn("[腾讯云OCR] 图片归一化失败，按原图调用: {}", e.getMessage());
            return imageBase64;
        }
    }

    private byte[] download(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new ApiException(502, "下载腾讯云处理结果失败 HTTP " + response.statusCode());
            }
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(502, "下载腾讯云处理结果被中断");
        } catch (Exception e) {
            log.error("下载腾讯云处理结果失败 url={}", abbreviate(url), e);
            throw new ApiException(502, "下载腾讯云处理结果失败");
        }
    }

    private HttpRequest signedRequest(String action, String payload, AppProperties.Ai.Tencent config)
            throws Exception {
        String host = config.endpoint();
        Instant now = Instant.now();
        long timestamp = now.getEpochSecond();
        String date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneOffset.UTC).format(now);
        String credentialScope = date + "/" + SERVICE + "/tc3_request";
        String canonicalHeaders = "content-type:application/json; charset=utf-8\nhost:" + host + "\n";
        String canonicalRequest = String.join("\n",
                "POST", "/", "", canonicalHeaders, "content-type;host", sha256Hex(payload));
        String stringToSign = String.join("\n",
                "TC3-HMAC-SHA256", String.valueOf(timestamp), credentialScope, sha256Hex(canonicalRequest));
        byte[] secretDate = hmacSha256(("TC3" + config.secretKey()).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, SERVICE);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = HexFormat.of().formatHex(hmacSha256(secretSigning, stringToSign));
        String authorization = "TC3-HMAC-SHA256 Credential=" + config.secretId() + "/" + credentialScope
                + ", SignedHeaders=content-type;host, Signature=" + signature;

        return HttpRequest.newBuilder(URI.create("https://" + host))
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", authorization)
                .header("X-TC-Action", action)
                .header("X-TC-Version", API_VERSION)
                .header("X-TC-Timestamp", String.valueOf(timestamp))
                .header("X-TC-Nonce", String.valueOf(ThreadLocalRandom.current().nextInt(1_000_000_000)))
                .header("X-TC-Region", config.region())
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
    }

    private String translate(String code, String message) {
        Map<String, String> messages = Map.ofEntries(
                Map.entry("AuthFailure.SecretIdNotFound", "SecretId 不存在，请检查腾讯云控制台密钥。"),
                Map.entry("AuthFailure.SignatureFailure", "签名校验失败，请确认 SecretId 和 SecretKey 来自同一密钥。"),
                Map.entry("AuthFailure.InvalidSecretKey", "SecretKey 无效，请检查腾讯云控制台密钥。"),
                Map.entry("FailedOperation.EngineRecognizeTimeout", "引擎处理超时，请稍后重试。"),
                Map.entry("FailedOperation.ImageDecodeFailed", "图片解码失败，请重新上传。"),
                Map.entry("FailedOperation.DownLoadError", "腾讯云下载文件失败，请稍后重试。"),
                Map.entry("InternalError", "腾讯云内部错误，请稍后重试。"),
                Map.entry("InvalidParameterValue.InvalidParameterValueLimit", "请求参数有误，请重试。"),
                Map.entry("LimitExceeded.TooLargeFileError", "图片过大，请压缩后重试。"),
                Map.entry("UnauthorizedOperation", "当前账号未开通文字识别服务或无权限调用。"));
        String translated = messages.get(code);
        return translated != null ? translated : (message == null || message.isBlank() ? "服务调用失败" : message);
    }

    private String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] hmacSha256(byte[] key, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private String abbreviate(String value) {
        if (value == null) return "";
        return value.length() <= 300 ? value : value.substring(0, 300) + "...";
    }
}
