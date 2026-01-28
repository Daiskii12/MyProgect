
package org.example.controllers;

import lombok.RequiredArgsConstructor;
import org.example.dto.statistics.Response;
import org.example.services.IndexingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class IndexingController {

    private final IndexingService indexingService;

    @GetMapping("/startIndexing")
    public ResponseEntity<Response> startIndexing() {
        boolean result = indexingService.startIndexing();

        Response response = new Response();
        response.setResult(result);

        if (!result) {
            response.setError("Индексация уже запущена");
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Response> stopIndexing() {
        boolean result = indexingService.stopIndexing();

        Response response = new Response();
        response.setResult(result);

        if (!result) {
            response.setError("Индексация не была запущена");
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/indexPage")
    public ResponseEntity<Response> indexPage(@RequestParam String url) {
        boolean result = indexingService.indexPage(url);

        Response response = new Response();
        response.setResult(result);

        if (!result) {
            response.setError("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }

        return ResponseEntity.ok(response);
    }
}