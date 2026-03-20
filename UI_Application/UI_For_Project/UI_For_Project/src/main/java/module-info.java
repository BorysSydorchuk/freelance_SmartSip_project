module org.myprojects.project_APP {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires java.net.http;
    requires org.json;

    opens org.myprojects.project_APP to javafx.fxml;
    exports org.myprojects.project_APP;
}

