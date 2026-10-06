package com.yingying.cuotiku.server.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingying.cuotiku.server.dto.AgentDto.*;
import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.dto.AuthDto.UserDto;
import com.yingying.cuotiku.server.dto.BookDto.EntryDto;
import com.yingying.cuotiku.server.dto.BookV2Dto.EntryDtoV2;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import com.yingying.cuotiku.server.service.AgentConfigService;
import com.yingying.cuotiku.server.service.AgentRuntimeService;
import com.yingying.cuotiku.server.service.BookService;
import com.yingying.cuotiku.server.service.BookV2Service;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class ClientAgentController {

    private static final List<String> ERROR_TYPES = List.of("马虎", "不会", "概念不清", "其他");

    private final AgentRuntimeService agentRuntimeService;
    private final BookService bookService;
    private final BookV2Service bookV2Service;

    public ClientAgentController(AgentRuntimeService agentRuntimeService, BookService bookService,
                                 BookV2Service bookV2Service) {
        this.agentRuntimeService = agentRuntimeService;
        this.bookService = bookService;
        this.bookV2Service = bookV2Service;
    }

    private record ResolvedInput(byte[] imageBytes, MimeType mime, Map<String, String> vars) {}

    @PostMapping("/analogy")
    public ApiResponse<AnalogyResponse> analogy(@Valid @RequestBody RunRequest request,
                                                @AuthenticationPrincipal AuthenticatedUser principal) {
        User user = principal.user();
        requireAi(user);
        ResolvedInput input = resolve(request, user);
        Map<String, String> vars = new java.util.HashMap<>(input.vars());
        vars.put("count", String.valueOf(request.count() == null ? 3 : request.count()));
        String entryKey = request.entryId() == null || request.entryId().isBlank() ? null : request.entryId().trim();
        AgentRuntimeService.AgentRawResult result =
                agentRuntimeService.run(AgentConfigService.ANALOGY, vars, input.imageBytes(), input.mime(), user, entryKey);
        List<AnalogyItem> items = new ArrayList<>();
        for (JsonNode node : result.json().path("items")) {
            List<String> options = new ArrayList<>();
            for (JsonNode option : node.path("options")) {
                options.add(option.asText(""));
            }
            items.add(new AnalogyItem(
                    node.path("stem").asText(""),
                    options,
                    node.path("answer").asText(""),
                    node.path("analysis").asText(""),
                    node.path("difficulty").isInt() ? node.path("difficulty").asInt() : null));
        }
        return ApiResponse.ok(new AnalogyResponse(result.traceId(), result.model(), items, result.cached()));
    }

    @PostMapping("/explain")
    public ApiResponse<ExplainResponse> explain(@Valid @RequestBody RunRequest request,
                                                @AuthenticationPrincipal AuthenticatedUser principal) {
        User user = principal.user();
        requireAi(user);
        ResolvedInput input = resolve(request, user);
        Map<String, String> vars = new java.util.HashMap<>(input.vars());
        vars.put("count", "3");
        String entryKey = request.entryId() == null || request.entryId().isBlank() ? null : request.entryId().trim();
        AgentRuntimeService.AgentRawResult result =
                agentRuntimeService.run(AgentConfigService.EXPLAIN, vars, input.imageBytes(), input.mime(), user, entryKey);
        JsonNode json = result.json();
        List<ExplainStep> steps = new ArrayList<>();
        for (JsonNode node : json.path("steps")) {
            steps.add(new ExplainStep(node.path("title").asText(""), node.path("content").asText("")));
        }
        return ApiResponse.ok(new ExplainResponse(
                result.traceId(),
                result.model(),
                json.path("analysis").asText(""),
                steps,
                stringList(json.path("knowledgePoints")),
                stringList(json.path("commonMistakes")),
                json.path("summary").asText(""),
                result.cached()));
    }

    private void requireAi(User user) {
        if (!user.isAiEnabled()) {
            throw ApiException.forbidden("当前账号未开通AI权限，请联系管理员");
        }
    }

    private Map<String, String> baseVars(RunRequest request) {
        Map<String, String> vars = new HashMap<>();
        vars.put("subject", request.subject() == null ? "" : request.subject().trim());
        vars.put("topic", "");
        vars.put("grade", request.grade() == null ? "" : String.valueOf(request.grade()));
        vars.put("term", request.term() == null ? "不限" : (request.term() == 1 ? "上学期" : "下学期"));
        vars.put("errorType", request.errorType() == null ? "其他" : request.errorType());
        return vars;
    }

    private ResolvedInput resolve(RunRequest request, User user) {
        if (request.entryId() != null && !request.entryId().isBlank()) {
            EntryDto entry = bookService.get(user.getId(), request.entryId().trim());
            BookService.ImagePayload payload = bookService.getImage(user.getId(), entry.id(), "original");
            // 服务端解析当前分类名称（改名/重新归类后即时生效），不接受客户端伪造上下文
            EntryDtoV2 v2 = bookV2Service.toV2Dto(
                    bookService.requireForAgent(user.getId(), entry.id()));
            Map<String, String> vars = new HashMap<>();
            vars.put("subject", v2.subjectName() == null ? v2.subject() : v2.subjectName());
            vars.put("topic", v2.topicName() == null ? "" : v2.topicName());
            vars.put("grade", String.valueOf(entry.grade()));
            vars.put("term", entry.term() == null ? "不限" : (entry.term() == 1 ? "上学期" : "下学期"));
            vars.put("errorType", entry.errorType());
            return new ResolvedInput(payload.bytes(), MimeTypeUtils.parseMimeType(payload.contentType()), vars);
        }
        if (request.imageBase64() == null || request.imageBase64().isBlank()) {
            throw ApiException.badRequest("请提供 entryId 或 imageBase64");
        }
        if (request.subject() == null || request.subject().isBlank()
                || request.subject().trim().length() > 30) {
            throw ApiException.badRequest("请提供有效科目名称（1-30 字）");
        }
        if (request.grade() == null) {
            throw ApiException.badRequest("请提供年级");
        }
        if (request.errorType() != null && !ERROR_TYPES.contains(request.errorType())) {
            throw ApiException.badRequest("错误类型只能是：" + String.join("、", ERROR_TYPES));
        }
        String trimmed = request.imageBase64().trim();
        int comma = trimmed.indexOf(',');
        if (trimmed.startsWith("data:") && comma > 0) {
            trimmed = trimmed.substring(comma + 1);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("图片Base64内容无效");
        }
        return new ResolvedInput(bytes, detectMime(bytes), baseVars(request));
    }

    private MimeType detectMime(byte[] bytes) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                String format = readers.next().getFormatName().toLowerCase();
                if (format.contains("jpeg") || format.contains("jpg")) {
                    return MimeTypeUtils.IMAGE_JPEG;
                }
                if (format.contains("png")) {
                    return MimeTypeUtils.IMAGE_PNG;
                }
            }
        } catch (Exception e) {
            // 回退默认
        }
        return MimeTypeUtils.IMAGE_PNG;
    }

    private List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                values.add(item.asText(""));
            }
        }
        return values;
    }
}
