package io.github.ghals5737.loadmin.autoconfigure;

import java.util.List;

import io.github.ghals5737.loadmin.core.LoadTestEndpoint;
import io.github.ghals5737.loadmin.core.LoadTestEndpointScanner;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the loadmin backend API and redirects {@code /loadmin} to the UI
 * (static resources bundled in loadmin-ui at {@code /loadmin/index.html}).
 */
@RestController
public class LoadminEndpointController {

    private final LoadTestEndpointScanner scanner;

    public LoadminEndpointController(LoadTestEndpointScanner scanner) {
        this.scanner = scanner;
    }

    @GetMapping("/loadmin")
    public ResponseEntity<Void> index() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", "/loadmin/index.html")
                .build();
    }

    @GetMapping("/loadmin/api/endpoints")
    public List<LoadTestEndpoint> endpoints() {
        return scanner.scan();
    }
}
