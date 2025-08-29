package com.firecapture.ColourCorrectionPlugin;

import de.wonderplanets.firecapture.plugin.IFilter;
import de.wonderplanets.firecapture.plugin.IFilterListener;
import de.wonderplanets.firecapture.plugin.CamInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.*;
import java.util.Properties;
import java.awt.Rectangle;

public class ColourCorrectionPlugin implements IFilter {

    private String currentTitle = "Default";
    private Map<String, double[][]> matrices = new HashMap<>();
    private JButton configButton;
    private IFilterListener filterListener;

    // Bayer pattern selection
    private String bayerPattern = "RGGB"; // Default

    public ColourCorrectionPlugin() {
        loadMatrices(); // Load matrices on initialization
    }

    // Default matrix (identity)
    {
        matrices.put("Default", new double[][]{
            {1, 0, 0},
            {0, 1, 0},
            {0, 0, 1}
        });
    }

    @Override
    public String getInterfaceVersion() {
        return "1.1";
    }

    @Override
    public String getName() {
        return "Colour Correction Plugin";
    }

    @Override
    public boolean supportsMono() {
        return true; // Treat raw colour as mono
    }

    @Override
    public boolean supportsColor() {
        return false;
    }

    @Override
    public void computeMono(byte[] pixels, Rectangle rect, CamInfo camInfo) {
        int width = rect.width;
        int height = rect.height;
        // Process in 2x2 blocks
        for (int y = rect.y; y < rect.y + height - 1; y += 2) {
            for (int x = rect.x; x < rect.x + width - 1; x += 2) {
                // Get positions based on pattern
                int[] posR = getPosition("R", bayerPattern, x, y, width);
                int[] posG1 = getPosition("G1", bayerPattern, x, y, width);
                int[] posG2 = getPosition("G2", bayerPattern, x, y, width);
                int[] posB = getPosition("B", bayerPattern, x, y, width);

                double r = pixels[posR[1] * width + posR[0]] & 0xFF;
                double g1 = pixels[posG1[1] * width + posG1[0]] & 0xFF;
                double g2 = pixels[posG2[1] * width + posG2[0]] & 0xFF;
                double b = pixels[posB[1] * width + posB[0]] & 0xFF;

                double g = (g1 + g2) / 2.0;

                double[][] matrix = matrices.getOrDefault(currentTitle, matrices.get("Default"));

                double newR = matrix[0][0] * r + matrix[0][1] * g + matrix[0][2] * b;
                double newG = matrix[1][0] * r + matrix[1][1] * g + matrix[1][2] * b;
                double newB = matrix[2][0] * r + matrix[2][1] * g + matrix[2][2] * b;

                int clippedR = clip((int) newR);
                int clippedG = clip((int) newG);
                int clippedB = clip((int) newB);

                // Assign back
                pixels[posR[1] * width + posR[0]] = (byte) clippedR;
                pixels[posG1[1] * width + posG1[0]] = (byte) clippedG;
                pixels[posG2[1] * width + posG2[0]] = (byte) clippedG;
                pixels[posB[1] * width + posB[0]] = (byte) clippedB;
            }
        }
        // Note: If width or height not multiple of 2, the last row/column is unchanged.
    }

    // Get position [x, y] for colour in 2x2 block
    private int[] getPosition(String colour, String pattern, int baseX, int baseY, int width) {
        switch (pattern) {
            case "RGGB":
                if (colour.equals("R")) return new int[]{baseX, baseY};
                if (colour.equals("G1")) return new int[]{baseX + 1, baseY};
                if (colour.equals("G2")) return new int[]{baseX, baseY + 1};
                if (colour.equals("B")) return new int[]{baseX + 1, baseY + 1};
                break;
            case "BGGR":
                if (colour.equals("B")) return new int[]{baseX, baseY};
                if (colour.equals("G1")) return new int[]{baseX + 1, baseY};
                if (colour.equals("G2")) return new int[]{baseX, baseY + 1};
                if (colour.equals("R")) return new int[]{baseX + 1, baseY + 1};
                break;
            case "GRBG":
                if (colour.equals("G1")) return new int[]{baseX, baseY};
                if (colour.equals("R")) return new int[]{baseX + 1, baseY};
                if (colour.equals("B")) return new int[]{baseX, baseY + 1};
                if (colour.equals("G2")) return new int[]{baseX + 1, baseY + 1};
                break;
            case "GBRG":
                if (colour.equals("G1")) return new int[]{baseX, baseY};
                if (colour.equals("B")) return new int[]{baseX + 1, baseY};
                if (colour.equals("R")) return new int[]{baseX, baseY + 1};
                if (colour.equals("G2")) return new int[]{baseX + 1, baseY + 1};
                break;
        }
        return new int[]{0, 0}; // Default error
    }

    private int clip(int val) {
        return Math.max(0, Math.min(255, val));
    }

    @Override
    public JButton getButton() {
        if (configButton == null) {
            configButton = new JButton("Configure");
            configButton.addActionListener(e -> showConfigDialog());
        }
        return configButton;
    }

    private void showConfigDialog() {
        JDialog dialog = new JDialog();
        dialog.setTitle("Colour Correction Config");
        dialog.setLayout(new BorderLayout());

        // List of matrices
        java.util.List<String> matrixList = new java.util.ArrayList<>(matrices.keySet());
        JList<String> list = new JList<>(matrixList.toArray(new String[0]));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(list);
        dialog.add(scroll, BorderLayout.CENTER);

        // Bayer pattern selection
        JPanel patternPanel = new JPanel();
        patternPanel.add(new JLabel("Bayer Pattern:"));
        JComboBox<String> patternCombo = new JComboBox<>(new String[]{"RGGB", "BGGR", "GRBG", "GBRG"});
        patternCombo.setSelectedItem(bayerPattern);
        patternCombo.addActionListener(e -> {
            bayerPattern = (String) patternCombo.getSelectedItem();
            saveMatrices();
            if (filterListener != null) filterListener.filterDone(this);
        });
        patternPanel.add(patternCombo);
        dialog.add(patternPanel, BorderLayout.NORTH);

        // Buttons
        JPanel buttons = new JPanel();
        JButton add = new JButton("Add New Matrix");
        add.addActionListener(e -> addNewMatrix(dialog, list));
        JButton select = new JButton("Select Matrix");
        select.addActionListener(e -> {
            String selected = list.getSelectedValue();
            if (selected != null) {
                currentTitle = selected;
                if (filterListener != null) filterListener.filterDone(this);
            }
            dialog.dispose();
        });
        JButton delete = new JButton("Delete Matrix");
        delete.addActionListener(e -> {
            String selected = list.getSelectedValue();
            if (selected != null && !selected.equals("Default")) {
                matrices.remove(selected);
                matrixList.remove(selected);
                list.setListData(matrixList.toArray(new String[0]));
                saveMatrices();
            }
        });
        buttons.add(add);
        buttons.add(select);
        buttons.add(delete);
        dialog.add(buttons, BorderLayout.SOUTH);

        // No need to loadMatrices() here since done in constructor

        dialog.pack();
        dialog.setVisible(true);
    }

    private void addNewMatrix(JDialog parent, JList<String> list) {
        JDialog inputDialog = new JDialog(parent, "Add Matrix", true);
        inputDialog.setLayout(new GridLayout(6, 4)); // Adjusted to 6 rows, 4 columns for labels

        JTextField titleField = new JTextField("New Title");
        inputDialog.add(new JLabel("Title:"));
        inputDialog.add(titleField);
        inputDialog.add(new JLabel());
        inputDialog.add(new JLabel());

        // Column labels
        inputDialog.add(new JLabel()); // Empty for row labels
        inputDialog.add(new JLabel("From R"));
        inputDialog.add(new JLabel("From G"));
        inputDialog.add(new JLabel("From B"));

        JTextField[][] fields = new JTextField[3][3];
        String[] rowLabels = {"To R", "To G", "To B"};
        for (int i = 0; i < 3; i++) {
            inputDialog.add(new JLabel(rowLabels[i]));
            for (int j = 0; j < 3; j++) {
                fields[i][j] = new JTextField(i == j ? "1" : "0");
                inputDialog.add(fields[i][j]);
            }
        }

        // Save button spans the row
        JButton save = new JButton("Save");
        save.addActionListener(e -> {
            String title = titleField.getText();
            if (title.isEmpty() || matrices.containsKey(title)) {
                JOptionPane.showMessageDialog(inputDialog, "Invalid or duplicate title");
                return;
            }
            double[][] mat = new double[3][3];
            try {
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        mat[i][j] = Double.parseDouble(fields[i][j].getText());
                    }
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(inputDialog, "Invalid numbers");
                return;
            }
            matrices.put(title, mat);
            saveMatrices();
            inputDialog.dispose();
            // Refresh list
            list.setListData(new ArrayList<>(matrices.keySet()).toArray(new String[0]));
        });
        inputDialog.add(save);
        inputDialog.add(new JLabel());
        inputDialog.add(new JLabel());
        inputDialog.add(new JLabel());

        inputDialog.pack();
        inputDialog.setVisible(true);
    }

    private void loadMatrices() {
        File file = new File(System.getProperty("user.home") + "/.firecapture_colour_matrices.properties");
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                Properties props = new Properties();
                props.load(fis);
                bayerPattern = props.getProperty("bayerPattern", "RGGB");
                for (String key : props.stringPropertyNames()) {
                    if (key.startsWith("matrix.")) {
                        String title = key.substring(7);
                        String values = props.getProperty(key);
                        String[] parts = values.split(",");
                        if (parts.length == 9) {
                            double[][] mat = new double[3][3];
                            int idx = 0;
                            for (int i = 0; i < 3; i++) {
                                for (int j = 0; j < 3; j++) {
                                    mat[i][j] = Double.parseDouble(parts[idx++].trim());
                                }
                            }
                            matrices.put(title, mat);
                        }
                    }
                }
            } catch (Exception e) {
                // Silent
            }
        }
    }

    private void saveMatrices() {
        File file = new File(System.getProperty("user.home") + "/.firecapture_colour_matrices.properties");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            Properties props = new Properties();
            props.setProperty("bayerPattern", bayerPattern);
            for (Map.Entry<String, double[][]> entry : matrices.entrySet()) {
                StringBuilder sb = new StringBuilder();
                for (double[] row : entry.getValue()) {
                    for (double val : row) {
                        sb.append(val).append(",");
                    }
                }
                sb.deleteCharAt(sb.length() - 1);
                props.setProperty("matrix." + entry.getKey(), sb.toString());
            }
            props.store(fos, "Colour Matrices");
        } catch (Exception e) {
            // Silent
        }
    }

    @Override
    public void registerFilterListener(IFilterListener listener) {
        this.filterListener = listener;
    }

    @Override
    public String getStringUsage(int value) {
        return "Matrix: " + currentTitle + ", Pattern: " + bayerPattern;
    }

    // Stub implementations for missing methods

    @Override
    public void activated() {
        // Empty
    }

    @Override
    public void appendToLogfile(Properties props) {
        // Empty or add properties if needed
    }

    @Override
    public boolean capture() {
        // Empty
        return true;
    }

    @Override
    public void captureStarted() {
        // Empty
    }

    @Override
    public void captureStoped() {
        // Empty
    }

    @Override
    public void computeColor(int[] pixels, Rectangle rect, CamInfo camInfo) {
        // Empty, since not supported
    }

    @Override
    public void filterChanged(String oldFilter, String newFilter) {
        // Empty
    }

    @Override
    public String getCurrentValue() {
        return "";
    }

    @Override
    public String getCurrentValueLabel() {
        return "";
    }

    @Override
    public String getDescription() {
        return "Applies colour correction matrix to Bayer data. Author & Credit: Con Kolivas";
    }

    @Override
    public String getFilenameAppendix() {
        return "_colour_corrected";
    }

    @Override
    public int getInitialSliderValue() {
        return 0;
    }

    @Override
    public String getMaxValue() {
        return "";
    }

    @Override
    public String getMaxValueLabel() {
        return "";
    }

    @Override
    public void imageSizeChanged() {
        // Empty
    }

    @Override
    public boolean isNullFilter() {
        return false;
    }

    @Override
    public boolean processEarly() {
        return true; // Assume early processing for correction
    }

    @Override
    public void release() {
        // Empty
    }

    @Override
    public void sliderValueChanged(int value) {
        // Empty, no slider
    }

    @Override
    public boolean useSlider() {
        return false;
    }

    @Override
    public boolean useValueFields() {
        return false;
    }

}