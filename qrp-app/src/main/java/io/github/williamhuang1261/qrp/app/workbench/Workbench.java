package io.github.williamhuang1261.qrp.app.workbench;

import io.github.williamhuang1261.qrp.app.BacktestRunner;
import io.github.williamhuang1261.qrp.app.CliArguments;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/**
 * The research workbench: equity curve, drawdown and the metrics table for one
 * run.
 *
 * <p>Everything numeric lives in {@link WorkbenchModel}; this class only turns
 * those points into nodes. Run it with:
 *
 * <pre>mvn -pl qrp-app exec:java -Dexec.mainClass=io.github.williamhuang1261.qrp.app.workbench.Workbench</pre>
 *
 * <p>Passing {@code --snapshot out.png} renders the same scene to a file without
 * showing a window, which is how the README image is produced and how the layout
 * can be checked on a machine with no display attached.
 */
public final class Workbench extends Application {

    private static final double WIDTH = 1_180;
    private static final double HEIGHT = 720;

    @Override
    public void start(Stage stage) {
        List<String> arguments = getParameters().getRaw();
        String snapshotTarget = snapshotTarget(arguments);
        CliArguments parsed = CliArguments.parse(withoutSnapshot(arguments));

        reportComputeEngines();
        WorkbenchModel model = new WorkbenchModel(BacktestRunner.run(parsed));
        Scene scene = new Scene(buildRoot(model), WIDTH, HEIGHT);

        if (snapshotTarget != null) {
            writeSnapshot(scene, snapshotTarget);
            Platform.exit();
            return;
        }

        stage.setTitle("QRP Workbench — " + model.title());
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Prints which compute engine will be used and why the others will not.
     *
     * <p>The window shows the engine that ran; this says what was on offer. A
     * research tool that silently takes a slower path leaves the user guessing,
     * and the answer is one line long.
     */
    private static void reportComputeEngines() {
        for (io.github.williamhuang1261.qrp.core.spi.ComputeEngine engine
                : io.github.williamhuang1261.qrp.stats.ComputeEngines.discovered()) {
            System.out.printf("compute engine %-10s %s%n", engine.id(),
                    engine.unavailableReason().map(reason -> "unavailable: " + reason).orElse("available"));
        }
    }

    private BorderPane buildRoot(WorkbenchModel model) {
        Label title = new Label(model.title());
        title.setFont(Font.font("System", 20));
        Label subtitle = new Label(model.subtitle());
        subtitle.setFont(Font.font("System", 12));

        VBox header = new VBox(2, title, subtitle);
        header.setStyle("-fx-padding: 12 16 8 16;");

        LineChart<String, Number> equity = buildEquityChart(model);
        AreaChart<String, Number> drawdown = buildDrawdownChart(model);
        VBox.setVgrow(equity, Priority.ALWAYS);

        VBox charts = new VBox(6, equity, drawdown);
        charts.setStyle("-fx-padding: 0 8 8 8;");

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(charts);
        VBox side = new VBox(buildMetricsTable(model));
        side.setStyle("-fx-padding: 8 8 0 0;");
        root.setRight(side);
        return root;
    }

    private LineChart<String, Number> buildEquityChart(WorkbenchModel model) {
        CategoryAxis dates = new CategoryAxis();
        NumberAxis value = new NumberAxis();
        value.setForceZeroInRange(false);
        value.setLabel("account value");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("equity");
        model.equityPoints().forEach(point ->
                series.getData().add(new XYChart.Data<>(point.label(), point.value())));

        LineChart<String, Number> chart = new LineChart<>(dates, value);
        chart.getData().add(series);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(false);
        chart.setTitle("Equity");
        // A category axis with 500 dates prints an unreadable smear of labels.
        dates.setTickLabelsVisible(false);
        dates.setTickMarkVisible(false);
        return chart;
    }

    private AreaChart<String, Number> buildDrawdownChart(WorkbenchModel model) {
        CategoryAxis dates = new CategoryAxis();
        NumberAxis percent = new NumberAxis();
        percent.setLabel("drawdown %");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("drawdown");
        model.drawdownPoints().forEach(point ->
                series.getData().add(new XYChart.Data<>(point.label(), point.value())));

        AreaChart<String, Number> chart = new AreaChart<>(dates, percent);
        chart.getData().add(series);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(false);
        chart.setTitle("Drawdown");
        chart.setPrefHeight(220);
        dates.setTickLabelsVisible(false);
        dates.setTickMarkVisible(false);
        return chart;
    }

    private TableView<WorkbenchModel.MetricRow> buildMetricsTable(WorkbenchModel model) {
        TableColumn<WorkbenchModel.MetricRow, String> name = new TableColumn<>("metric");
        name.setCellValueFactory(row ->
                new javafx.beans.property.SimpleStringProperty(row.getValue().name()));
        name.setPrefWidth(170);

        TableColumn<WorkbenchModel.MetricRow, String> value = new TableColumn<>("value");
        value.setCellValueFactory(row ->
                new javafx.beans.property.SimpleStringProperty(row.getValue().value()));
        value.setPrefWidth(140);

        TableView<WorkbenchModel.MetricRow> table =
                new TableView<>(FXCollections.observableArrayList(model.metricRows()));
        table.getColumns().add(name);
        table.getColumns().add(value);
        table.setPrefWidth(330);
        table.setStyle("-fx-padding: 0 8 8 0;");
        // Size to content: a fixed-height table under a dozen rows is mostly
        // empty ruled lines, which reads as missing data rather than as space.
        table.setFixedCellSize(26);
        table.setPrefHeight(model.metricRows().size() * 26 + 30);
        table.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        return table;
    }

    private void writeSnapshot(Scene scene, String target) {
        WritableImage image = scene.snapshot(null);
        File file = new File(target);
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try {
            javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
            System.out.println("wrote " + file.getAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("could not write " + target, e);
        }
    }

    private static String snapshotTarget(List<String> arguments) {
        int index = arguments.indexOf("--snapshot");
        if (index < 0) {
            return null;
        }
        if (index + 1 >= arguments.size()) {
            throw new IllegalArgumentException("--snapshot needs a file path");
        }
        return arguments.get(index + 1);
    }

    private static List<String> withoutSnapshot(List<String> arguments) {
        int index = arguments.indexOf("--snapshot");
        if (index < 0) {
            return arguments;
        }
        List<String> remaining = new java.util.ArrayList<>(arguments);
        remaining.subList(index, index + 2).clear();
        return remaining;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
