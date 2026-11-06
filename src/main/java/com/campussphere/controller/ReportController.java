package com.campussphere.controller;

import com.campussphere.app.AppContext;
import com.campussphere.model.*;
import com.campussphere.ui.*;
import com.campussphere.task.Background;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Parent;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import java.util.*;
import java.util.stream.Collectors;

public final class ReportController {
    private final AppContext context;
    private final TableView<Row> table=new TableView<>(); private final VBox result=new VBox(18);
    private final Label status=Ui.label("Choose a module and grouping to generate a live report.","muted");
    private final ComboBox<ModuleSpec> modules=new ComboBox<>(); private final ComboBox<FieldSpec> fields=new ComboBox<>();
    public ReportController(AppContext context) {this.context=context;}
    public Parent view() {
        modules.setId("report-module");fields.setId("report-field");table.setId("report-results");
        modules.setItems(FXCollections.observableArrayList(context.campus().modules())); modules.setConverter(new StringConverter<>() {public String toString(ModuleSpec m){return m==null?"":m.title();}public ModuleSpec fromString(String s){return null;}}); modules.setPrefWidth(270);
        fields.setConverter(new StringConverter<>() {public String toString(FieldSpec f){return f==null?"":f.label();}public FieldSpec fromString(String s){return null;}});fields.setPrefWidth(240);
        modules.setOnAction(e->{fields.getItems().setAll(modules.getValue().fields().stream().filter(f->!Set.of("textarea","password","email").contains(f.type())).toList());fields.getSelectionModel().selectFirst();});modules.getSelectionModel().selectFirst();
        Button generate=Ui.button("Generate report","primary",this::generate);generate.setId("report-generate");
        FlowPane controls=new FlowPane(12,12,modules,fields,generate);
        TableColumn<Row,String> group=new TableColumn<>("Group");group.setCellValueFactory(c->new SimpleStringProperty(c.getValue().text("group")));group.setPrefWidth(500);
        TableColumn<Row,String> total=new TableColumn<>("Records");total.setCellValueFactory(c->new SimpleStringProperty(c.getValue().text("count")));total.setPrefWidth(150);table.getColumns().add(group);table.getColumns().add(total);table.setPrefHeight(260);table.setPlaceholder(Ui.label("Generate a report to see its records.","muted"));
        result.getChildren().add(table);
        VBox root=new VBox(22,Ui.label("Analytics & reports","page-title"),Ui.label("Turn your stored campus records into a clear view.","muted"),controls,status,Ui.card(result)); root.getStyleClass().add("module-page");return Ui.scroll(root);
    }
    private void generate() {
        ModuleSpec module=modules.getValue();FieldSpec field=fields.getValue();if(module==null||field==null)return;status.setText("Calculating report…");
        Background.run(()->{
            Map<Long,String> references=new HashMap<>(); if(field.type().equals("reference"))context.campus().options(field.reference()).forEach(r->references.put(r.id(),RecordForm.describe(r)));
            Map<String,Long> grouped=context.campus().list(module.key()).stream().collect(Collectors.groupingBy(r->field.type().equals("reference")?references.getOrDefault(r.number(field.key()),"Unassigned"):r.text(field.key()).isBlank()?"Not specified":r.text(field.key()),TreeMap::new,Collectors.counting()));
            List<Row> report=new ArrayList<>(); grouped.forEach((key,value)->report.add(new Row(report.size()+1,Map.of("group",key,"count",value))));return report;
        },rows->{
            table.getItems().setAll(rows);BarChart<String,Number> chart=new BarChart<>(new CategoryAxis(),new NumberAxis());chart.setAnimated(false);chart.setLegendVisible(false);chart.setPrefHeight(280);XYChart.Series<String,Number> series=new XYChart.Series<>();rows.stream().limit(12).forEach(r->series.getData().add(new XYChart.Data<>(r.text("group"),r.number("count"))));chart.getData().add(series);result.getChildren().setAll(chart,table);status.setText(module.title()+" by "+field.label().toLowerCase()+" · "+rows.stream().mapToLong(r->r.number("count")).sum()+" records · "+rows.size()+" groups (chart shows first 12)");
        },e->status.setText(Ui.safeMessage(e)));
    }
}
