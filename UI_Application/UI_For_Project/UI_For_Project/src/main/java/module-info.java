module org.myprojects.test_ui_2 {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires java.net.http;
    requires org.json;

    opens org.myprojects.test_ui_2 to javafx.fxml;
    exports org.myprojects.test_ui_2;
    exports org.myprojects.DB_task;
    exports org.myprojects.project_APP;
    opens org.myprojects.DB_task to javafx.fxml;
}
