package com.samhanair.logis.product.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 시트 dump (workbook.json + formulas.json) 읽기.
 *
 * <p><b>출처</b>: migration/source/sheet/dump-script.gs (Apps Script getDisplayValues() dump).
 * 시트 파일은 .gitignore 에 의해 worktree 외부 (C:/dev/SamhanLogis/migration/source/sheet/) 에 위치.
 *
 * <p>본 reader 는 환경변수 {@code SEED_SHEET_DIR} 또는 system property {@code seed.sheet.dir} 로
 * 위치 지정. 미지정 시 default = {@code ../../../../migration/source/sheet/}.
 */
public class SheetWorkbookReader {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path sheetDir;
    private JsonNode workbook;
    private JsonNode formulas;

    public SheetWorkbookReader(Path sheetDir) {
        this.sheetDir = sheetDir;
    }

    public static SheetWorkbookReader fromEnvOrDefault() {
        String envDir = System.getenv("SEED_SHEET_DIR");
        if (envDir == null) envDir = System.getProperty("seed.sheet.dir");
        if (envDir == null) {
            // worktree 외부 default (개발 환경 기준)
            envDir = "../../../../migration/source/sheet";
        }
        return new SheetWorkbookReader(Path.of(envDir));
    }

    public boolean isAvailable() {
        return Files.exists(sheetDir.resolve("workbook.json"));
    }

    public Path getSheetDir() {
        return sheetDir;
    }

    /** workbook.json lazy load. */
    public JsonNode workbook() throws IOException {
        if (workbook == null) {
            try (InputStream in = Files.newInputStream(sheetDir.resolve("workbook.json"))) {
                workbook = mapper.readTree(in);
            }
        }
        return workbook;
    }

    /** formulas.json lazy load. */
    public JsonNode formulas() throws IOException {
        if (formulas == null) {
            try (InputStream in = Files.newInputStream(sheetDir.resolve("formulas.json"))) {
                formulas = mapper.readTree(in);
            }
        }
        return formulas;
    }

    /**
     * 시트 탭의 values 2D array → row 별 cell 값 list.
     */
    public List<List<String>> sheetValues(String sheetName) throws IOException {
        JsonNode tab = workbook().get(sheetName);
        if (tab == null) return List.of();
        JsonNode values = tab.get("values");
        if (values == null || !values.isArray()) return List.of();
        List<List<String>> rows = new ArrayList<>(values.size());
        for (JsonNode rowNode : values) {
            List<String> row = new ArrayList<>(rowNode.size());
            for (JsonNode cell : rowNode) {
                row.add(cell == null || cell.isNull() ? "" : cell.asText(""));
            }
            rows.add(row);
        }
        return rows;
    }

    /** 시트 탭의 formula 2D array. formulas 가 없는 경우 빈 List 반환. */
    public List<List<String>> sheetFormulas(String sheetName) throws IOException {
        JsonNode tab = formulas().get(sheetName);
        if (tab == null) return List.of();
        // formulas.json 형식 체크 (workbook 과 동일 가정)
        JsonNode values = tab.get("formulas");
        if (values == null) values = tab.get("values");
        if (values == null || !values.isArray()) return List.of();
        List<List<String>> rows = new ArrayList<>(values.size());
        for (JsonNode rowNode : values) {
            List<String> row = new ArrayList<>(rowNode.size());
            for (JsonNode cell : rowNode) {
                row.add(cell == null || cell.isNull() ? "" : cell.asText(""));
            }
            rows.add(row);
        }
        return rows;
    }

    /** 헤더 row (col 0 에 '품 명' 또는 '품명' 포함) 위치 자동 탐색 — 시트별 헤더 위치 가변 가드. */
    public int findHeaderRow(List<List<String>> rows) {
        for (int i = 0; i < Math.min(10, rows.size()); i++) {
            List<String> r = rows.get(i);
            if (!r.isEmpty()) {
                String c0 = r.get(0);
                if (c0 != null && c0.contains("품") && c0.contains("명")) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** 헤더 컬럼명 → 컬럼 index 맵 (정규화 — 공백 제거). */
    public Map<String, Integer> headerIndexMap(List<String> headerRow) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String h = headerRow.get(i);
            if (h != null && !h.isBlank()) {
                m.put(normalize(h), i);
            }
        }
        return m;
    }

    public static String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").trim();
    }
}
