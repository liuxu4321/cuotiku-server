package com.yingying.cuotiku.server.web;

import com.yingying.cuotiku.server.dto.ApiResponse;
import com.yingying.cuotiku.server.dto.BookDto.PracticeHistoryDto;
import com.yingying.cuotiku.server.security.AuthenticatedUser;
import com.yingying.cuotiku.server.service.BookService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/book/practices")
public class BookPracticeHistoryController {

    private final BookService bookService;

    public BookPracticeHistoryController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/history")
    public ApiResponse<PracticeHistoryDto> history(
            @RequestParam(required = false) Boolean correct,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(bookService.history(principal.user().getId(), correct, start, end, page, size));
    }
}
