package com.yingying.cuotiku.server.service;

import com.yingying.cuotiku.server.web.ApiException;

import java.text.Normalizer;
import java.util.Locale;

/**
 * CONTRACT §3 名称规范化：NFC → 去首尾空白（含全角空格）→ 内部连续空白折叠 → 小写折叠（normalized）。
 * 码点计数 1–30。
 */
public final class NameNormalizer {

    public static final int MAX_CODE_POINTS = 30;

    private NameNormalizer() {
    }

    public static String display(String raw) {
        if (raw == null) {
            throw ApiException.badRequest("名称不能为空");
        }
        String nfc = Normalizer.normalize(raw, Normalizer.Form.NFC);
        String trimmed = trimEdges(nfc);
        String collapsed = trimmed.replaceAll("[\\s\u3000]+", " ");
        return collapsed;
    }

    public static String normalized(String raw) {
        return display(raw).toLowerCase(Locale.ROOT);
    }

    public static String validate(String raw, String what) {
        String name = display(raw);
        int codePoints = name.codePointCount(0, name.length());
        if (codePoints < 1) {
            throw ApiException.badRequest(what + "名称不能为空");
        }
        if (codePoints > MAX_CODE_POINTS) {
            throw ApiException.badRequest(what + "名称最长 " + MAX_CODE_POINTS + " 个字符，请缩短后重试");
        }
        return name;
    }

    private static String trimEdges(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isEdgeWhitespace(value.charAt(start))) {
            start += 1;
        }
        while (end > start && isEdgeWhitespace(value.charAt(end - 1))) {
            end -= 1;
        }
        return value.substring(start, end);
    }

    private static boolean isEdgeWhitespace(char c) {
        return c == '\u3000' || Character.isWhitespace(c);
    }
}
