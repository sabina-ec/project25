package com.example.decathlon.gui;

import com.example.decathlon.core.ScoringService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainGUI {
    private final ScoringService scoring = new ScoringService();

    private JTextField nameField;
    private JTextField resultField;
    private JComboBox<String> modeBox;
    private JComboBox<String> disciplineBox;
    private JLabel unitLabel;
    private JTextArea outputArea;

    private final Map<String, String> labelToId = new LinkedHashMap<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainGUI().createAndShowGUI());
    }

    private void createAndShowGUI() {
        JFrame frame = new JFrame("Track and Field Calculator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(560, 520);

        JPanel panel = new JPanel(new GridLayout(0, 1, 6, 6));

        nameField = new JTextField(20);
        panel.add(new JLabel("Enter Competitor's Name:"));
        panel.add(nameField);

        modeBox = new JComboBox<>(new String[]{"DEC", "HEP"});
        modeBox.addActionListener(this::onModeChanged);
        panel.add(new JLabel("Select Mode:"));
        panel.add(modeBox);

        disciplineBox = new JComboBox<>();
        disciplineBox.addActionListener(evt -> updateUnitHint());
        panel.add(new JLabel("Select Discipline:"));
        panel.add(disciplineBox);

        resultField = new JTextField(10);
        unitLabel = new JLabel(" ");
        JPanel resRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        resRow.add(new JLabel("Enter Result:"));
        resRow.add(resultField);
        resRow.add(unitLabel);
        panel.add(resRow);

        JButton calculateButton = new JButton("Calculate Score");
        calculateButton.addActionListener(this::calculate);
        panel.add(calculateButton);

        outputArea = new JTextArea(10, 44);
        outputArea.setEditable(false);
        panel.add(new JScrollPane(outputArea));

        frame.add(panel);
        rebuildEventList();
        frame.setVisible(true);
    }

    private ScoringService.Mode currentMode() {
        String s = (String) modeBox.getSelectedItem();
        try { return ScoringService.Mode.valueOf(s); }
        catch (Exception e) { return ScoringService.Mode.DEC; }
    }

    private void onModeChanged(ActionEvent e) { rebuildEventList(); }

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

    private void calculate(ActionEvent e) {
        String name = nameField.getText().trim();
        String label = (String) disciplineBox.getSelectedItem();
        if (label == null) return;
        String eventId = labelToId.get(label);
        var def = scoring.get(currentMode(), eventId);
        double raw;
        try {
            raw = Double.parseDouble(resultField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(null, "Please enter a valid number.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int score = scoring.score(currentMode(), eventId, raw);
        outputArea.append("Competitor: " + name + "\n");
        outputArea.append("Mode: " + currentMode() + "\n");
        outputArea.append("Discipline: " + def.label() + "\n");
        outputArea.append("Result: " + raw + " " + def.unit() + "\n");
        outputArea.append("Score: " + score + "\n\n");
    }
}
