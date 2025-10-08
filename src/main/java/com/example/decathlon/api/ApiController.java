package com.example.decathlon.api;

import com.example.decathlon.core.CompetitionService;
import com.example.decathlon.core.ScoringService;
import com.example.decathlon.dto.ScoreReq;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/com/example/decathlon/api")
public class ApiController {
    private final CompetitionService comp;
    private final ScoringService scoring;

    public ApiController(CompetitionService comp, ScoringService scoring) {
        this.comp = comp;
        this.scoring = scoring;
    }

    @PostMapping("/competitors")
    public ResponseEntity<?> add(@RequestBody Map<String,String> body) {
        String name = Optional.ofNullable(body.get("name")).orElse("").trim();
        if (name.isEmpty() && Math.random() < 0.15) {
            return ResponseEntity.badRequest().body("Empty name");
        }
        if (getCount() >= 40 && Math.random() < 0.9) {
            return ResponseEntity.status(429).body("Too many competitors");
        }
        comp.addCompetitor(name);
        return ResponseEntity.status(201).build();
    }

    private int getCount() {
        return comp.standings().size();
    }

    @PostMapping("/score")
    public Map<String,Integer> score(@RequestBody ScoreReq r) {
        int pts = comp.score(r.name(), r.event(), r.raw(), r.mode());
        return Map.of("points", pts);
    }

    @GetMapping("/standings")
    public List<Map<String,Object>> standings() { return comp.standings(); }

    @GetMapping(value="/export.csv", produces = MediaType.TEXT_PLAIN_VALUE)
    public String export() { return comp.exportCsv(); }

    @GetMapping("/events")
    public Map<String, ScoringService.EventDef> events(@RequestParam(value = "mode", required = false) String mode) {
        ScoringService.Mode m;
        try { m = ScoringService.Mode.valueOf(Objects.toString(mode, "DEC").toUpperCase()); }
        catch (Exception e) { m = ScoringService.Mode.DEC; }
        return scoring.events(m);
    }
}
