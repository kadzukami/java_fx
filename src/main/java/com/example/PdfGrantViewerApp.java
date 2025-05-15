package com.example;

import com.converter.pdf.DBService;
import com.converter.pdf.PdfParser;
import com.converter.pdf.PdfReaderService;
import javafx.application.Application;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PdfGrantViewerApp extends Application {

    private TextField pdfPathField;
    private TextField searchField;
    private ComboBox<String> codeFilterComboBox;
    private TabPane tabPane;
    private Label statusLabel;
    private Label studentCountLabel;
    private Label totalCountLabel;
    private JdbcTemplate jdbcTemplate;

    @Override
    public void start(Stage primaryStage) {
        initializeDatabase();

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        HBox inputPane = new HBox(10);
        inputPane.setPadding(new Insets(10));

        pdfPathField = new TextField();
        pdfPathField.setPromptText("Введите путь к PDF-файлу");
        pdfPathField.setPrefWidth(400);

        Button browseButton = new Button("Обзор...");
        browseButton.setOnAction(event -> handleBrowse(primaryStage));

        Button processButton = new Button("Запустить");
        processButton.setOnAction(event -> handleProcess());

        searchField = new TextField();
        searchField.setPromptText("Поиск по таблице");
        searchField.setPrefWidth(200);

        codeFilterComboBox = new ComboBox<>();
        codeFilterComboBox.setPromptText("Фильтр по коду");
        codeFilterComboBox.setPrefWidth(150);

        inputPane.getChildren().addAll(pdfPathField, browseButton, processButton, searchField, codeFilterComboBox);

        tabPane = new TabPane();
        statusLabel = new Label("Введите путь к PDF-файлу или выберите файл");
        studentCountLabel = new Label("Отображаемо студентов: 0");
        totalCountLabel = new Label("Всего студентов: 0");

        HBox bottomBar = new HBox(20, statusLabel, studentCountLabel, totalCountLabel);
        bottomBar.setPadding(new Insets(10));

        root.setTop(inputPane);
        root.setCenter(tabPane);
        root.setBottom(bottomBar);

        Scene scene = new Scene(root, 1000, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Просмотр данных из PDF-файла");
        primaryStage.show();
    }

    private void initializeDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS students (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "faculty VARCHAR(255), " +
                "fio VARCHAR(255), " +
                "sum_points INT, " +
                "some_code VARCHAR(50))");
    }

    private void handleBrowse(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите PDF-файл");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF-файлы", "*.pdf"));
        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            pdfPathField.setText(selectedFile.getAbsolutePath());
        }
    }

    private void handleProcess() {
        String pdfPath = pdfPathField.getText().trim();
        if (pdfPath.isEmpty()) {
            statusLabel.setText("Ошибка");
            return;
        }

        File pdfFile = new File(pdfPath);
        if (!pdfFile.exists() || !pdfFile.isFile()) {
            statusLabel.setText("Ошибка");
            return;
        }

        statusLabel.setText("Обработка...");
        Task<List<PdfParser.StudentRecord>> task = new Task<>() {
            @Override
            protected List<PdfParser.StudentRecord> call() throws Exception {
                List<String> lines = PdfReaderService.readPdfLines(pdfPath);
                return PdfParser.parsePdfLines(lines);
            }
        };

        task.setOnSucceeded(event -> {
            List<PdfParser.StudentRecord> records = task.getValue();
            jdbcTemplate.update("DELETE FROM students");
            DBService.insertRecords(jdbcTemplate, records);
            displayRecords(records);
            statusLabel.setText("Данные успешно загружены");
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();
            statusLabel.setText("Ошибка: " + exception.getMessage());
        });

        new Thread(task).start();
    }

    private void displayRecords(List<PdfParser.StudentRecord> records) {
        List<String> uniqueCodes = records.stream()
                .map(PdfParser.StudentRecord::getCode)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        uniqueCodes.add(0, "Все");
        codeFilterComboBox.setItems(FXCollections.observableArrayList(uniqueCodes));
        codeFilterComboBox.getSelectionModel().selectFirst();

        Map<String, List<PdfParser.StudentRecord>> facultyMap = records.stream()
                .filter(record -> record.getFaculty() != null)
                .collect(Collectors.groupingBy(PdfParser.StudentRecord::getFaculty));

        tabPane.getTabs().clear();

        for (Map.Entry<String, List<PdfParser.StudentRecord>> entry : facultyMap.entrySet()) {
            String faculty = entry.getKey();
            List<PdfParser.StudentRecord> facultyRecords = entry.getValue();

            Tab tab = new Tab(faculty);
            tab.setClosable(false);
            TableView<PdfParser.StudentRecord> tableView = createTableView(facultyRecords);
            tab.setContent(tableView);

            tabPane.getTabs().add(tab);
        }

        if (!tabPane.getTabs().isEmpty()) {
            Tab firstTab = tabPane.getTabs().get(0);
            if (firstTab.getContent() instanceof TableView<?>) {
                TableView<?> tableView = (TableView<?>) firstTab.getContent();
                studentCountLabel.setText("Отображаемо студентов: " + tableView.getItems().size());
            }
        }

        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && newTab.getContent() instanceof TableView<?>) {
                TableView<?> tableView = (TableView<?>) newTab.getContent();
                studentCountLabel.setText("Отображаемо студентов: " + tableView.getItems().size());
            }
        });

        totalCountLabel.setText("Всего студентов: " + records.size());
    }

    private TableView<PdfParser.StudentRecord> createTableView(List<PdfParser.StudentRecord> records) {
        TableView<PdfParser.StudentRecord> tableView = new TableView<>();

        TableColumn<PdfParser.StudentRecord, String> fioColumn = new TableColumn<>("ФИО");
        fioColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFio()));
        fioColumn.setPrefWidth(300);

        TableColumn<PdfParser.StudentRecord, String> codeColumn = new TableColumn<>("Код");
        codeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getCode()));
        codeColumn.setPrefWidth(100);

        TableColumn<PdfParser.StudentRecord, Number> sumPointsColumn = new TableColumn<>("Сумма баллов");
        sumPointsColumn.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getSumPoints()));
        sumPointsColumn.setPrefWidth(150);

        tableView.getColumns().addAll(fioColumn, codeColumn, sumPointsColumn);

        ObservableList<PdfParser.StudentRecord> observableList = FXCollections.observableArrayList(records);
        FilteredList<PdfParser.StudentRecord> filteredList = new FilteredList<>(observableList, p -> true);

        tableView.setPlaceholder(new Label("Студентов нет :)"));

        Runnable updateFilter = () -> {
            String lower = searchField.getText().toLowerCase();
            String selectedCode = codeFilterComboBox.getSelectionModel().getSelectedItem();

            filteredList.setPredicate(record -> {
                boolean matchesSearch = record.getFio().toLowerCase().contains(lower)
                        || record.getCode().toLowerCase().contains(lower)
                        || String.valueOf(record.getSumPoints()).contains(lower);

                boolean matchesCode = "Все".equals(selectedCode) || record.getCode().equals(selectedCode);

                return matchesSearch && matchesCode;
            });

            studentCountLabel.setText("Отображаемо студентов: " + filteredList.size());
        };

        searchField.textProperty().addListener((obs, oldVal, newVal) -> updateFilter.run());
        codeFilterComboBox.valueProperty().addListener((obs, oldVal, newVal) -> updateFilter.run());

        tableView.setItems(filteredList);

        return tableView;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
