package com.yingying.cuotiku.server.web;

import com.yingying.cuotiku.server.dto.AdminUserDto.PageResponse;
import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.dto.BookDto.*;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import com.yingying.cuotiku.server.service.BookService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/book/entries")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @PostMapping
    public ApiResponse<EntryDto> add(@Valid @RequestBody AddEntryRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(bookService.addEntry(request, principal.user()));
    }

    @GetMapping
    public ApiResponse<PageResponse<EntryDto>> list(
            @RequestParam(required = false) Integer grade,
            @RequestParam(required = false) Integer term,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String errorType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(bookService.list(principal.user().getId(), grade, term, subject, errorType, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<EntryDto> get(@PathVariable String id,
                                     @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(bookService.get(principal.user().getId(), id));
    }

    @PostMapping("/images")
    public ApiResponse<List<ImageItem>> images(@Valid @RequestBody ImagesRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(bookService.getImages(principal.user().getId(), request.ids(), request.kind()));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable String id,
                                        @RequestParam(defaultValue = "original") String kind,
                                        @AuthenticationPrincipal AuthenticatedUser principal) {
        BookService.ImagePayload payload = bookService.getImage(principal.user().getId(), id, kind);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .body(payload.bytes());
    }

    @PutMapping("/{id}")
    public ApiResponse<EntryDto> update(@PathVariable String id,
                                        @Valid @RequestBody UpdateEntryRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(bookService.update(principal.user().getId(), id, request));
    }

    @PostMapping("/random")
    public ApiResponse<RandomPaperResponse> random(@Valid @RequestBody RandomPaperRequest request,
                                                   @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(bookService.randomPaper(principal.user().getId(), request));
    }

    @PostMapping("/practice")
    public ApiResponse<Map<String, Integer>> practice(@Valid @RequestBody PracticeRequest request,
                                                      @AuthenticationPrincipal AuthenticatedUser principal) {
        int updated = bookService.bumpPractice(principal.user().getId(), request.ids());
        return ApiResponse.ok(Map.of("updated", updated));
    }

    @PostMapping("/{id}/practices")
    public ApiResponse<PracticeRecordDto> addPractice(@PathVariable String id,
                                                      @Valid @RequestBody PracticeRecordRequest request,
                                                      @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(bookService.addPractice(principal.user().getId(), id, request));
    }

    @GetMapping("/{id}/practices")
    public ApiResponse<PracticeHistoryDto> listPractices(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(bookService.listPractices(principal.user().getId(), id, page, size));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id,
                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        bookService.delete(principal.user().getId(), id);
        return ApiResponse.ok(null);
    }
}
