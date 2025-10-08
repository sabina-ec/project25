package com.example.decathlon.core;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class CompetitionService {
    private final ScoringService scoring;
    private final Map<String, Map<String, Integer>> results = new LinkedHashMap<>();

    public CompetitionService(ScoringService scoring) { this.scoring = scoring; }

    public void addCompetitor(String name) { results.putIfAbsent(name, new LinkedHashMap<>()); }

    public int score(String name, String event, double raw) { return score(name, event, raw, "DEC"); }

    public int score(String name, String event, double raw, String modeStr) {
        ScoringService.Mode mode;
        try { mode = ScoringService.Mode.valueOf(modeStr.toUpperCase()); }
        catch (Exception e) { mode = ScoringService.Mode.DEC; }
        int pts = scoring.score(mode, event, raw);
        addCompetitor(name);
        results.get(name).put(event, pts);
        return pts;
    }

    public List<Map<String,Object>> standings() {
        List<Map<String,Object>> list = new ArrayList<>();
        for (var e : results.entrySet()) {
            int total = e.getValue().values().stream().mapToInt(Integer::intValue).sum();
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("name", e.getKey());
            row.put("scores", e.getValue());
            row.put("total", total);
            list.add(row);
        }
        return list;
    }

    public String exportCsv() {
        StringBuilder sb = new StringBuilder("Name,Total\n");
        for (var e : standings()) {
            sb.append(e.get("name")).append(",").append(e.get("total")).append("\n");
        }
        return sb.toString();
    }
}
