package com.campussphere.util;

import com.campussphere.model.Row;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.*;
import java.util.*;

public final class CsvExporter {
    private CsvExporter() { }
    public static void export(Path path,List<String> columns,List<Row> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path,StandardCharsets.UTF_8)) {
            writer.write('\ufeff'); writer.write(String.join(",",columns.stream().map(CsvExporter::escape).toList())); writer.newLine();
            for (Row row : rows) { writer.write(String.join(",",columns.stream().map(row::text).map(CsvExporter::escape).toList())); writer.newLine(); }
        }
    }
    public static String escape(String text) {
        if (text == null) text = "";
        // Neutralize spreadsheet formulas, including formula characters after whitespace.
        String stripped = text.stripLeading();
        if (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"","\"\"") + "\"";
    }
}
