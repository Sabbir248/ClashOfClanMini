module com.clashgame.client {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.logging;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind;

    opens com.clashgame.client to javafx.fxml;
    opens com.clashgame.client.controller to javafx.fxml;
    opens com.clashgame.client.model to javafx.base, com.fasterxml.jackson.databind;

    exports com.clashgame.client;
    exports com.clashgame.client.model;
    exports com.clashgame.client.controller;
    exports com.clashgame.client.network;
    exports com.clashgame.client.game;
    exports com.clashgame.client.util;
    exports com.clashgame.server.model;
}
