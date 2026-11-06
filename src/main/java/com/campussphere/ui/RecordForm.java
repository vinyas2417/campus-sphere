package com.campussphere.ui;

import com.campussphere.model.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

/** A metadata-driven editor shared by modules and specific workflow commands. */
public final class RecordForm {
    public record Choice(long id, String label) { @Override public String toString() { return label; } }
    private final Dialog<Void> dialog = new Dialog<>();
    private final Map<FieldSpec,Node> controls = new LinkedHashMap<>();
    private final Label error = Ui.label("", "error-text");
    private final Button submit;
    public RecordForm(String title, String action, List<FieldSpec> fields, Map<String,Object> initial, Map<String,List<Row>> options) {
        dialog.setTitle(title); dialog.setHeaderText(title); Ui.dialogStyle(dialog);
        GridPane grid = new GridPane(); grid.setHgap(20); grid.setVgap(16); grid.setPadding(new Insets(8));
        int index = 0;
        for (FieldSpec spec : fields) {
            Node control = control(spec, initial.get(spec.key()), options); control.setId("field-"+spec.key()); controls.put(spec,control);
            VBox cell = new VBox(7, Ui.label(spec.label() + (spec.required() ? " *" : ""), "field-label"), control);
            cell.setMinWidth(235); cell.setMaxWidth(Double.MAX_VALUE); GridPane.setHgrow(cell, Priority.ALWAYS);
            grid.add(cell, index % 2, index / 2); index++;
        }
        error.setWrapText(true); VBox body = new VBox(14, grid, error); body.setPadding(new Insets(4));
        ScrollPane scroll = Ui.scroll(body); scroll.setPrefViewportWidth(590); scroll.setMaxHeight(560);
        dialog.getDialogPane().setContent(scroll);
        ButtonType save = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,save);
        submit = (Button) dialog.getDialogPane().lookupButton(save); submit.getStyleClass().add("primary");
        submit.setId("record-submit");
        submit.setMinWidth(150);
    }
    private Node control(FieldSpec f,Object value,Map<String,List<Row>> options) {
        String text = value == null ? "" : value.toString();
        if (f.type().equals("reference")) {
            ComboBox<Choice> combo = new ComboBox<>(); combo.setMaxWidth(Double.MAX_VALUE);
            if (!f.required()) combo.getItems().add(new Choice(0,"None"));
            options.getOrDefault(f.reference(),List.of()).forEach(r -> combo.getItems().add(new Choice(r.id(), describe(r))));
            combo.getItems().stream().filter(c -> Long.toString(c.id()).equals(text)).findFirst().ifPresent(combo::setValue);
            combo.setPromptText("Choose " + f.label().toLowerCase()); return combo;
        }
        if (f.type().equals("choice")) {
            ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(f.choices())); combo.setMaxWidth(Double.MAX_VALUE);
            if (!text.isEmpty()) combo.setValue(text); else if (!f.choices().isEmpty()) combo.setValue(f.choices().getFirst()); return combo;
        }
        if (f.type().equals("date")) {
            DatePicker picker = new DatePicker(); if (!text.isEmpty()) try { picker.setValue(LocalDate.parse(text)); } catch (Exception ignored) { }
            picker.setConverter(new javafx.util.StringConverter<LocalDate>() {
                @Override public String toString(LocalDate value) { return value == null ? "" : value.toString(); }
                @Override public LocalDate fromString(String value) { return value == null || value.isBlank() ? null : LocalDate.parse(value.trim()); }
            });
            picker.setPromptText("yyyy-mm-dd"); picker.setMaxWidth(Double.MAX_VALUE); return picker;
        }
        if (f.type().equals("textarea")) { TextArea area = new TextArea(text); area.setPrefRowCount(3); area.setWrapText(true); return area; }
        TextField input = f.type().equals("password") ? new PasswordField() : new TextField(); input.setText(text);
        if (f.type().equals("datetime")) input.setPromptText("yyyy-mm-ddTHH:mm");
        else if (f.type().equals("integer")) input.setPromptText("Whole number");
        else if (f.key().contains("time")) input.setPromptText("HH:mm (24-hour)");
        return input;
    }
    public void show(Consumer<Map<String,Object>> onSave) {
        submit.addEventFilter(javafx.event.ActionEvent.ACTION,event -> {
            event.consume();
            try { Map<String,Object> values = values(); error.setText(""); onSave.accept(values); }
            catch (IllegalArgumentException ex) { error.setText(ex.getMessage()); }
        }); dialog.show(); Ui.animate(dialog.getDialogPane());
    }
    public void busy(boolean busy) { submit.setDisable(busy); error.setText(busy ? "Saving your changes…" : ""); }
    public void fail(Throwable failure) { busy(false); error.setText(Ui.safeMessage(failure)); }
    public void close() { dialog.close(); }
    private Map<String,Object> values() {
        Map<String,Object> values = new LinkedHashMap<>();
        controls.forEach((f,node) -> {
            Object value;
            if (node instanceof DatePicker date) {
                String typed = date.getEditor().getText();
                try { date.commitValue(); } catch (Exception ex) { throw new IllegalArgumentException("Enter a valid date for " + f.label() + "."); }
                value = date.getValue() == null ? null : date.getValue().toString();
            } else if (node instanceof ComboBox<?> combo) {
                Object selected = combo.getValue(); value = selected instanceof Choice c ? (c.id() == 0 ? null : c.id()) : selected;
            } else value = ((TextInputControl)node).getText().trim();
            if (value instanceof String s && s.isBlank()) value = null;
            if (f.required() && value == null) throw new IllegalArgumentException("Enter " + f.label().toLowerCase() + ".");
            if (value != null) try {
                value = switch (f.type()) { case "integer" -> Long.parseLong(value.toString()); case "decimal" -> new java.math.BigDecimal(value.toString()); case "datetime" -> LocalDateTime.parse(value.toString()).toString(); default -> value; };
            } catch (Exception ex) { throw new IllegalArgumentException("Check the format of " + f.label().toLowerCase() + "."); }
            values.put(f.key(),value);
        }); return values;
    }
    public static String describe(Row row) {
        for (String key : List.of("name","title","subject","username","usn","code")) if (!row.text(key).isBlank()) return row.text(key) + "  ·  #" + row.id();
        return "#" + row.id() + "  " + row.values().values().stream().filter(Objects::nonNull).limit(2).map(Object::toString).reduce((a,b)->a+" · "+b).orElse("");
    }
    public static FieldSpec field(String key,String title,String type) { return new FieldSpec(key,title,type,true,"",List.of()); }
    public static FieldSpec reference(String key,String title,String table) { return new FieldSpec(key,title,"reference",true,table,List.of()); }
    public static FieldSpec choice(String key,String title,String... values) { return new FieldSpec(key,title,"choice",true,"",List.of(values)); }
}
