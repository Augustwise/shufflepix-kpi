package com.shufflepix.client.core;

import com.shufflepix.client.core.effect.EffectRegistry;
import com.shufflepix.client.core.history.Command;
import com.shufflepix.client.core.history.CommandHistory;
import com.shufflepix.client.core.history.DrawCommand;
import com.shufflepix.client.core.model.Project;
import com.shufflepix.client.core.shape.CanvasPreviewRenderer;
import com.shufflepix.client.core.shape.Primitive;
import com.shufflepix.client.core.style.Color;
import com.shufflepix.client.core.style.Style;
import com.shufflepix.client.core.style.StyleFactory;
import com.shufflepix.client.core.tool.PencilTool;
import com.shufflepix.client.core.tool.Tool;
import com.shufflepix.client.core.tool.ToolContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class Editor implements ToolContext {
    public static final String DEFAULT_FONT = "System";

    private final CommandHistory history = new CommandHistory();
    private final StyleFactory styles = new StyleFactory();
    private final EffectRegistry effects = EffectRegistry.withDefaults();
    private final List<Runnable> changeListeners = new ArrayList<>();

    private Project project = Project.blank(800, 600, Color.WHITE);
    private Tool tool = PencilTool.pencil();
    private CanvasPreviewRenderer preview;
    private BiConsumer<Double, Double> textInputHandler = (x, y) -> { };

    private Color strokeColor = Color.BLACK;
    private Color fillColor = Color.WHITE;
    private boolean fillEnabled;
    private double lineWidth = 2;
    private String fontFamily = DEFAULT_FONT;
    private double fontSize = 16;

    public Editor() {
        history.setOnChange(this::fireChanged);
    }

    public void setProject(Project project) {
        this.project = project;
        history.clear();
    }

    public void execute(Command command) {
        history.execute(command);
    }

    public void undo() {
        history.undo();
    }

    public void redo() {
        history.redo();
    }

    public boolean canDrawOnActiveLayer() {
        return project.getActiveLayer().isVisible();
    }

    @Override
    public Project getProject() {
        return project;
    }

    @Override
    public Style getCurrentStyle() {
        return styles.get(strokeColor, fillEnabled ? fillColor : null, lineWidth, fontFamily, fontSize);
    }

    @Override
    public Style getEraserStyle() {
        return styles.get(Color.BLACK, null, lineWidth, fontFamily, fontSize);
    }

    @Override
    public CanvasPreviewRenderer getPreview() {
        return preview;
    }

    @Override
    public void commit(Primitive primitive) {
        execute(new DrawCommand(project.getActiveLayer(), primitive));
        project.markDirty();
    }

    @Override
    public void requestTextInput(double x, double y) {
        textInputHandler.accept(x, y);
    }

    public void setPreview(CanvasPreviewRenderer preview) {
        this.preview = preview;
    }

    public void setTextInputHandler(BiConsumer<Double, Double> handler) {
        this.textInputHandler = handler;
    }

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void fireChanged() {
        changeListeners.forEach(Runnable::run);
    }

    public CommandHistory getHistory() {
        return history;
    }

    public EffectRegistry getEffects() {
        return effects;
    }

    public Tool getTool() {
        return tool;
    }

    public void setTool(Tool tool) {
        this.tool = tool;
    }

    public Color getStrokeColor() {
        return strokeColor;
    }

    public void setStrokeColor(Color strokeColor) {
        this.strokeColor = strokeColor;
    }

    public Color getFillColor() {
        return fillColor;
    }

    public void setFillColor(Color fillColor) {
        this.fillColor = fillColor;
    }

    public boolean isFillEnabled() {
        return fillEnabled;
    }

    public void setFillEnabled(boolean fillEnabled) {
        this.fillEnabled = fillEnabled;
    }

    public double getLineWidth() {
        return lineWidth;
    }

    public void setLineWidth(double lineWidth) {
        this.lineWidth = lineWidth;
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    public double getFontSize() {
        return fontSize;
    }

    public void setFontSize(double fontSize) {
        this.fontSize = fontSize;
    }
}
