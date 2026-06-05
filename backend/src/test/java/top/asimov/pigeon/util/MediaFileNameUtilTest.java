package top.asimov.pigeon.util;

import org.junit.jupiter.api.Test;
import java.text.Normalizer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class MediaFileNameUtilTest {

    @Test
    public void testGetSafeTitle_Korean() {
        String koreanTitle = "한국어 제목";
        String safeTitle = MediaFileNameUtil.getSafeTitle(koreanTitle);
        
        // Before fix, this would fail because safeTitle would be in NFD form
        // NFD of "한국어" is "\u1112\u1161\u11AB\u1100\u116E\u11A8\u110B\u1165"
        // NFC of "한국어" is "\uD55C\uAD6D\uC5B4"
        
        assertFalse(Normalizer.isNormalized(safeTitle, Normalizer.Form.NFD), "Should not be NFD");
        assertEquals(Normalizer.normalize(koreanTitle, Normalizer.Form.NFC), safeTitle, "Korean characters should be in NFC form");
    }

    @Test
    public void testGetSafeTitle_Accents() {
        String accentedTitle = "éàïôǔ";
        String safeTitle = MediaFileNameUtil.getSafeTitle(accentedTitle);
        
        // Should still remove accents
        assertEquals("eaiou", safeTitle);
    }

    @Test
    public void testSanitizeFileName() {
        assertEquals("hello_world", MediaFileNameUtil.sanitizeFileName("hello / world"));
        assertEquals("safe-title", MediaFileNameUtil.sanitizeFileName("safe—title")); // em dash to hyphen
        assertEquals("untitled", MediaFileNameUtil.sanitizeFileName(null));
        assertEquals("untitled", MediaFileNameUtil.sanitizeFileName("   "));
    }
}
