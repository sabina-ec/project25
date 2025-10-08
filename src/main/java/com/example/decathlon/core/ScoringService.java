package com.example.decathlon.core;

import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ScoringService {
    public enum Type { TRACK, FIELD }
    public enum Mode { DEC, HEP }
    public record EventDef(String id, String label, Type type, double A, double B, double C, String unit) {}

    private final Map<String, EventDef> decEvents = new LinkedHashMap<>() {{
        put("100m",         new EventDef("100m",        "100m",           Type.TRACK, 25.4347, 18.0,   1.81, "s"));
        put("longJump",     new EventDef("longJump",    "Long Jump",      Type.FIELD, 0.14354, 220.0,  1.40, "cm"));
        put("shotPut",      new EventDef("shotPut",     "Shot Put",       Type.FIELD, 51.39,     1.5,  1.05, "m"));
        put("highJump",     new EventDef("highJump",    "High Jump",      Type.FIELD, 0.8465,   75.0,  1.42, "cm"));
        put("400m",         new EventDef("400m",        "400m",           Type.TRACK, 1.53775,  82.0,  1.81, "s"));
        put("110mHurdles",  new EventDef("110mHurdles", "110m Hurdles",   Type.TRACK, 5.74352,  28.5,  1.92, "s"));
        put("discus",       new EventDef("discus",      "Discus Throw",   Type.FIELD, 12.91,     4.0,  1.10, "m"));
        put("poleVault",    new EventDef("poleVault",   "Pole Vault",     Type.FIELD, 0.2797,  100.0,  1.35, "cm"));
        put("javelin",      new EventDef("javelin",     "Javelin Throw",  Type.FIELD, 10.14,     7.0,  1.08, "m"));
        put("1500m",        new EventDef("1500m",       "1500m",          Type.TRACK, 0.03768, 480.0,  1.85, "s"));
    }};

    private final Map<String, EventDef> hepEvents = new LinkedHashMap<>() {{
        put("100mHurdles",  new EventDef("100mHurdles", "100m Hurdles",   Type.TRACK, 9.23076, 26.7,  1.835, "s"));
        put("highJump",     new EventDef("highJump",    "High Jump",      Type.FIELD, 1.84523, 75.0,  1.348, "cm"));
        put("shotPut",      new EventDef("shotPut",     "Shot Put",       Type.FIELD, 56.0211,  1.5,  1.05,  "m"));
        put("200m",         new EventDef("200m",        "200m",           Type.TRACK, 4.99087, 42.5,  1.81,  "s"));
        put("longJump",     new EventDef("longJump",    "Long Jump",      Type.FIELD, 0.188807,210.0, 1.41,  "cm"));
        put("javelin",      new EventDef("javelin",     "Javelin Throw",  Type.FIELD, 15.9803,  3.8,  1.04,  "m"));
        put("800m",         new EventDef("800m",        "800m",           Type.TRACK, 0.11193,254.0, 1.88,  "s"));
    }};

    public Map<String, EventDef> events(Mode mode) {
        return mode == Mode.HEP ? hepEvents : decEvents;
    }

    public EventDef get(Mode mode, String id) {
        return events(mode).get(id);
    }

    public int score(Mode mode, String eventId, double raw) {
        EventDef e = get(mode, eventId);
        if (e == null) return 0;
        double points;
        if (e.type == Type.TRACK) {
            double x = e.B - raw;
            if (x <= 0) return 0;
            points = e.A * Math.pow(x, e.C);
        } else {
            double x = raw - e.B;
            if (x <= 0) return 0;
            points = e.A * Math.pow(x, e.C);
        }
        return (int)Math.floor(points);
    }
}
