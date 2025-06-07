module org.example.informsa_bot {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires com.almasb.fxgl.all;
    requires telegrambots;
    requires telegrambots.meta;
    requires org.json;
    requires com.google.auth.oauth2;
    requires google.api.client;
    requires com.google.api.client.json.jackson2;
    requires com.fasterxml.jackson.core;
    requires com.google.api.client;
    requires google.api.services.sheets.v4.rev612;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires java.sql;


    opens org.example.informsa_bot to javafx.fxml;
    exports org.example.informsa_bot;
}
