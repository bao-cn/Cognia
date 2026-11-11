package bao.gabriel.cognia.common.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamingTextSanitizerTest {

    @Test
    void removesAsciiAndFullWidthMarkers() {
        assertEquals("keep\n", sanitize("<|DSML| calls>\nkeep\n"));
        assertEquals("keep\n", sanitize("<｜｜DSML｜｜ calls>\n<｜｜DSML｜｜ invoke name=\"x\">\nkeep\n"));
        assertEquals("keep\n", sanitize("< ｜ ｜ DSML ｜ ｜ calls>\nkeep\n"));
    }

    @Test
    void removesMarkerSplitAcrossChunks() {
        assertEquals("正常回答\n", sanitize("<｜", "｜DSML｜｜ calls>\n正常回答\n"));
    }

    @Test
    void removesProtocolContinuationLines() {
        assertEquals("正常回答\n", sanitize(
                "<｜", "｜DSML｜｜ calls>\n inv", "oke name=\"web_search\">\n",
                " parameter name=\"q\">x\n</｜｜DSML｜｜ calls>\n正常回答\n"));
    }

    @Test
    void keepsOrdinaryHtmlAndProse() {
        assertEquals("<p>hello</p>\n", sanitize("<p>hello</p>\n"));
        assertEquals("正常回答\n第二行", sanitize("正常回答\n第二行"));
    }

    private String sanitize(String... chunks) {
        StreamingTextSanitizer sanitizer = new StreamingTextSanitizer();
        StringBuilder out = new StringBuilder();
        for (String chunk : chunks) {
            out.append(sanitizer.accept(chunk));
        }
        out.append(sanitizer.flush());
        return out.toString();
    }
}
