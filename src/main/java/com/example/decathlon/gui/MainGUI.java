package com.example.decathlon.gui;

import com.example.decathlon.core.ScoringService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class MainGUI {
    private final ScoringService scoring = new ScoringService();

    private JTextField nameField;
    private JTextField resultField;
    private JComboBox<String> modeBox;
    private JComboBox<String> disciplineBox;
    private JLabel unitLabel;
    private JTextArea outputArea;

    private JTable standingsTable;
    private DefaultTableModel standingsModel;

    private final Map<String, String> labelToId = new LinkedHashMap<>();
    private final Map<String, Map<String, Double>> rawResultsByAthlete = new LinkedHashMap<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainGUI().createAndShowGUI());
    }

    private void createAndShowGUI() {
        JFrame frame = new JFrame("Track and Field Calculator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(880, 720);

        JPanel root = new JPanel();
        root.setLayout(new BorderLayout(8, 8));

        // --- Top section ---
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        namePanel.add(new JLabel("Enter Competitor's Name:"));
        nameField = new JTextField(20);
        namePanel.add(nameField);
        JButton addCompetitorBtn = new JButton("Add competitor");
        addCompetitorBtn.addActionListener(this::onAddCompetitor);
        namePanel.add(addCompetitorBtn);
        topPanel.add(namePanel);

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modePanel.add(new JLabel("Select Mode:"));
        modeBox = new JComboBox<>(new String[]{"DEC", "HEP"});
        modeBox.addActionListener(this::onModeChanged);
        modePanel.add(modeBox);
        topPanel.add(modePanel);

        JPanel disciplinePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        disciplinePanel.add(new JLabel("Select Discipline:"));
        disciplineBox = new JComboBox<>();
        disciplineBox.addActionListener(evt -> updateUnitHint());
        disciplinePanel.add(disciplineBox);
        topPanel.add(disciplinePanel);

        JPanel resultPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        resultPanel.add(new JLabel("Enter Result:"));
        resultField = new JTextField(10);
        resultPanel.add(resultField);
        unitLabel = new JLabel(" ");
        resultPanel.add(unitLabel);
        topPanel.add(resultPanel);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton saveResultBtn = new JButton("Save result");
        saveResultBtn.addActionListener(this::saveResult);
        JButton exportBtn = new JButton("Export CSV");
        exportBtn.addActionListener(this::exportCSV);
        JButton importBtn = new JButton("Import CSV");
        importBtn.addActionListener(this::importCSV);
        actions.add(saveResultBtn);
        actions.add(exportBtn);
        actions.add(importBtn);
        topPanel.add(actions);

        outputArea = new JTextArea(8, 60);
        outputArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(outputArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Activity log"));
        topPanel.add(logScroll);

        // --- Standings section (bottom) ---
        standingsModel = new DefaultTableModel();
        standingsTable = new JTable(standingsModel);
        standingsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JScrollPane standingsScroll = new JScrollPane(standingsTable);
        standingsScroll.setBorder(BorderFactory.createTitledBorder("Standings"));
        standingsScroll.setPreferredSize(new Dimension(860, 250));

        root.add(topPanel, BorderLayout.NORTH);
        root.add(standingsScroll, BorderLayout.CENTER);

        frame.setContentPane(root);
        rebuildEventList();
        rebuildStandingsColumns();
        frame.setVisible(true);
    }

    private ScoringService.Mode currentMode() {
        String s = (String) modeBox.getSelectedItem();
        try { return ScoringService.Mode.valueOf(s); }
        catch (Exception e) { return ScoringService.Mode.DEC; }
    }

    private void onModeChanged(ActionEvent e) {
        rawResultsByAthlete.clear();
        rebuildEventList();
        rebuildStandingsColumns();
        refreshStandings();
    }

    private void rebuildEventList() {
        labelToId.clear();
        disciplineBox.removeAllItems();
        for (var def : scoring.events(currentMode()).values()) {
            labelToId.put(def.label(), def.id());
            disciplineBox.addItem(def.label());
        }
        updateUnitHint();
    }

    private void updateUnitHint() {
        String label = (String) disciplineBox.getSelectedItem();
        if (label == null) { unitLabel.setText(" "); return; }
        var def = scoring.get(currentMode(), labelToId.get(label));
        unitLabel.setText("(" + def.unit() + ")");
    }

    private void onAddCompetitor(ActionEvent e) {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Enter a name.", "Missing name", JOptionPane.WARNING_MESSAGE);
            return;
        }
        rawResultsByAthlete.putIfAbsent(name, new HashMap<>());
        outputArea.append("Added competitor: " + name + "\n");
        nameField.setText("");
        refreshStandings();
    }

    private void saveResult(ActionEvent e) {
        String name = nameField.getText().trim();
        if (name.isEmpty() || !rawResultsByAthlete.containsKey(name)) {
            JOptionPane.showMessageDialog(null, "Add competitor first.", "No competitor", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String label = (String) disciplineBox.getSelectedItem();
        if (label == null) return;
        String eventId = labelToId.get(label);

        String input = resultField.getText().trim().replace(',', '.');
        double raw;
        try {
            raw = Double.parseDouble(input);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(null, "Please enter a valid number.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int score = scoring.score(currentMode(), eventId, raw);
        rawResultsByAthlete.get(name).put(eventId, raw);

        var def = scoring.get(currentMode(), eventId);
        outputArea.append("Competitor: " + name + "\n");
        outputArea.append("Mode: " + currentMode() + "\n");
        outputArea.append("Discipline: " + def.label() + "\n");
        outputArea.append("Result: " + raw + " " + def.unit() + "\n");
        outputArea.append("Score: " + score + "\n\n");

        resultField.setText("");
        refreshStandings();
    }

    // --- Standings & helpers (identiskt som tidigare) ---
    private List<String> eventOrderIds() {
        return scoring.events(currentMode()).values()
                .stream().map(ScoringService.EventDef::id).toList();
    }

    private List<String> eventOrderLabels() {
        return scoring.events(currentMode()).values()
                .stream().map(ScoringService.EventDef::label).toList();
    }

    private void rebuildStandingsColumns() {
        List<String> labels = eventOrderLabels();
        List<String> cols = new ArrayList<>();
        cols.add("Rank");
        cols.add("Name");
        cols.addAll(labels);
        cols.add("Total");
        standingsModel.setDataVector(new Object[0][0], cols.toArray());
        autoSizeColumns();
    }

    private void refreshStandings() {
        Map<String, Integer> totals = new HashMap<>();
        Map<String, Map<String, Integer>> perEventPoints = new HashMap<>();
        ScoringService.Mode mode = currentMode();
        List<String> ids = eventOrderIds();

        for (var entry : rawResultsByAthlete.entrySet()) {
            String name = entry.getKey();
            Map<String, Double> rawMap = entry.getValue();
            int sum = 0;
            Map<String, Integer> ptsMap = new HashMap<>();
            for (String id : ids) {
                Double raw = rawMap.get(id);
                if (raw != null) {
                    int pts = scoring.score(mode, id, raw);
                    ptsMap.put(id, pts);
                    sum += pts;
                }
            }
            perEventPoints.put(name, ptsMap);
            totals.put(name, sum);
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(totals.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        List<Object[]> rows = new ArrayList<>();
        int rank = 0;
        int prevScore = Integer.MIN_VALUE;
        int position = 0;
        for (var e : sorted) {
            position++;
            int total = e.getValue();
            if (total != prevScore) {
                rank = position;
                prevScore = total;
            }
            List<Object> row = new ArrayList<>();
            row.add(rank);
            row.add(e.getKey());
            Map<String, Integer> pts = perEventPoints.getOrDefault(e.getKey(), Map.of());
            for (String id : ids) {
                Integer p = pts.get(id);
                row.add(p == null ? "" : p);
            }
            row.add(total);
            rows.add(row.toArray());
        }

        standingsModel.setRowCount(0);
        for (Object[] r : rows) standingsModel.addRow(r);
        autoSizeColumns();
    }

    private void autoSizeColumns() {
        for (int col = 0; col < standingsTable.getColumnCount(); col++) {
            int width = 80;
            for (int row = 0; row < standingsTable.getRowCount(); row++) {
                Component comp = standingsTable.prepareRenderer(standingsTable.getCellRenderer(row, col), row, col);
                width = Math.max(comp.getPreferredSize().width + 16, width);
            }
            standingsTable.getColumnModel().getColumn(col).setPreferredWidth(Math.min(width, 220));
        }
    }



    private void exportCSV(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export CSV");
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return;

        File file = ensureCsvExtension(chooser.getSelectedFile());
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            pw.println("MODE," + currentMode().name());
            List<String> ids = eventOrderIds();
            List<String> header = new ArrayList<>();
            header.add("Name");
            header.addAll(ids);
            header.add("Total");
            pw.println(String.join(",", header));

            for (String name : rawResultsByAthlete.keySet()) {
                Map<String, Double> rawMap = rawResultsByAthlete.get(name);
                List<String> cells = new ArrayList<>();
                cells.add(escapeCsv(name));
                int total = 0;
                for (String id : ids) {
                    Double raw = rawMap.get(id);
                    cells.add(raw == null ? "" : stripTrailingZeros(raw));
                    if (raw != null) total += scoring.score(currentMode(), id, raw);
                }
                cells.add(Integer.toString(total));
                pw.println(String.join(",", cells));
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void importCSV(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import CSV");
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String first = br.readLine();
            if (first == null || !first.startsWith("MODE,")) {
                JOptionPane.showMessageDialog(null, "Invalid file: missing MODE header.", "Invalid CSV", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String modeStr = first.split(",", 2)[1].trim();
            ScoringService.Mode fileMode = ScoringService.Mode.valueOf(modeStr);
            if (fileMode != currentMode()) {
                modeBox.setSelectedItem(fileMode.name());
            }

            String header = br.readLine();
            if (header == null) {
                JOptionPane.showMessageDialog(null, "Invalid file: missing columns.", "Invalid CSV", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String[] cols = splitCsv(header);
            if (cols.length < 2 || !"Name".equals(cols[0])) {
                JOptionPane.showMessageDialog(null, "Invalid file: bad header.", "Invalid CSV", JOptionPane.ERROR_MESSAGE);
                return;
            }

            List<String> ids = eventOrderIds();
            List<String> fileIds = new ArrayList<>();
            for (int i = 1; i < cols.length; i++) {
                if ("Total".equalsIgnoreCase(cols[i])) break;
                fileIds.add(cols[i]);
            }
            if (!fileIds.equals(ids)) {
                JOptionPane.showMessageDialog(null, "Events in file do not match current mode.", "Invalid CSV", JOptionPane.ERROR_MESSAGE);
                return;
            }

            rawResultsByAthlete.clear();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] c = splitCsv(line);
                if (c.length < 1) continue;
                String name = unescapeCsv(c[0]).trim();
                if (name.isEmpty()) continue;
                rawResultsByAthlete.putIfAbsent(name, new HashMap<>());

                for (int i = 0; i < ids.size(); i++) {
                    int idx = i + 1;
                    if (idx >= c.length) break;
                    String v = c[idx].trim();
                    if (!v.isEmpty()) {
                        try {
                            double raw = Double.parseDouble(v.replace(',', '.'));
                            rawResultsByAthlete.get(name).put(ids.get(i), raw);
                        } catch (NumberFormatException ignore) {
                        }
                    }
                }
            }
            refreshStandings();
            JOptionPane.showMessageDialog(null, "Import complete.", "Import", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "Import failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static File ensureCsvExtension(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (!n.endsWith(".csv")) return new File(f.getParentFile(), f.getName() + ".csv");
        return f;
    }

    private static String stripTrailingZeros(double d) {
        String s = Double.toString(d);
        if (s.contains(".")) {
            while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String escapeCsv(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String unescapeCsv(String s) {
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
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else {
                if (ch == ',') {
                    parts.add(cur.toString());
                    cur.setLength(0);
                } else if (ch == '"') {
                    inQuotes = true;
                } else {
                    cur.append(ch);
                }
            }
        }
        parts.add(cur.toString());
        return parts.toArray(new String[0]);
    }
}
