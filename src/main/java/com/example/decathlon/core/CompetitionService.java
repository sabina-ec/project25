package com.example.decathlon.core;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class CompetitionService {
    private final ScoringService scoring;
    private final Map<String, Map<String, Double>> raw = new LinkedHashMap<>();
    private ScoringService.Mode currentMode = ScoringService.Mode.DEC;

    public CompetitionService(ScoringService scoring) { this.scoring = scoring; }

    public void addCompetitor(String name) { raw.putIfAbsent(name, new LinkedHashMap<>()); }

    public int score(String name, String event, double value) { return score(name, event, value, "DEC"); }

    public int score(String name, String event, double value, String modeStr) {
        ScoringService.Mode mode;
        try { mode = ScoringService.Mode.valueOf(modeStr.toUpperCase()); }
        catch (Exception e) { mode = ScoringService.Mode.DEC; }
        currentMode = mode;
        addCompetitor(name);
        raw.get(name).put(event, value);
        return scoring.score(mode, event, value);
    }

    public List<Map<String,Object>> standings() {
        List<Map<String,Object>> list = new ArrayList<>();
        for (var e : raw.entrySet()) {
            String name = e.getKey();
            Map<String, Double> r = e.getValue();
            Map<String, Integer> pts = new LinkedHashMap<>();
            int total = 0;
            for (var id : scoring.events(currentMode).keySet()) {
                Double v = r.get(id);
                if (v != null) {
                    int p = scoring.score(currentMode, id, v);
                    pts.put(id, p);
                    total += p;
                }
            }
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("scores", pts);
            row.put("total", total);
            list.add(row);
        }
        return list;
    }

    public String exportCsv(String modeOpt) {
        ScoringService.Mode m = currentMode;
        if (modeOpt != null) {
            try { m = ScoringService.Mode.valueOf(modeOpt.toUpperCase()); } catch (Exception ignored) {}
        }
        StringBuilder sb = new StringBuilder();
        sb.append("MODE,").append(m.name()).append("\n");
        List<String> ids = new ArrayList<>(scoring.events(m).keySet());
        sb.append("Name,").append(String.join(",", ids)).append(",Total\n");
        for (var name : raw.keySet()) {
            Map<String, Double> r = raw.get(name);
            int total = 0;
            StringBuilder row = new StringBuilder();
            row.append(escape(name));
            for (String id : ids) {
                row.append(",");
                Double v = r.get(id);
                if (v != null) {
                    row.append(strip(v));
                    total += scoring.score(m, id, v);
                }
            }
            row.append(",").append(total);
            sb.append(row).append("\n");
        }
        return sb.toString();
    }

    public void importCsv(String csv) {
        if (csv == null) return;
        String[] lines = csv.split("\\R");
        if (lines.length < 2) return;
        String first = lines[0].trim();
        if (!first.startsWith("MODE,")) return;
        String modeStr = first.substring(5).trim();
        try { currentMode = ScoringService.Mode.valueOf(modeStr.toUpperCase()); } catch (Exception ignored) {}
        String header = lines[1];
        String[] cols = splitCsv(header);
        if (cols.length < 2 || !"Name".equals(cols[0])) return;
        List<String> ids = new ArrayList<>();
        for (int i = 1; i < cols.length; i++) {
            if ("Total".equalsIgnoreCase(cols[i])) break;
            ids.add(cols[i]);
        }
        raw.clear();
        for (int i = 2; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] c = splitCsv(line);
            if (c.length == 0) continue;
            String name = unescape(c[0]).trim();
            if (name.isEmpty()) continue;
            addCompetitor(name);
            for (int j = 0; j < ids.size(); j++) {
                int idx = j + 1;
                if (idx >= c.length) break;
                String v = c[idx].trim();
                if (!v.isEmpty()) {
                    try {
                        double val = Double.parseDouble(v.replace(',', '.'));
                        raw.get(name).put(ids.get(j), val);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
    }

    private static String strip(double d) {
        String s = Double.toString(d);
        if (s.contains(".")) {
            while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String escape(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String unescape(String s) {
        String t = s.trim();
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1).replace("\"\"", "\"");
        }
        return t;
    }

    private static String[] splitCsv(String line) {
        List<String> parts = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else { inQuotes = false; }
                } else cur.append(ch);
            } else {
                if (ch == ',') { parts.add(cur.toString()); cur.setLength(0); }
                else if (ch == '"') inQuotes = true;
                else cur.append(ch);
            }
        }
        parts.add(cur.toString());
        return parts.toArray(new String[0]);
    }
}
