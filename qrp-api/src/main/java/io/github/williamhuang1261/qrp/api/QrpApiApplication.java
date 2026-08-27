package io.github.williamhuang1261.qrp.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A third front end over {@link io.github.williamhuang1261.qrp.app.BacktestRunner},
 * alongside the CLI and the JavaFX workbench: this one talks HTTP instead of a
 * terminal or a window.
 */
@SpringBootApplication
public class QrpApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(QrpApiApplication.class, args);
    }
}
