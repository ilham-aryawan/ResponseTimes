package com.replywise.api;

import com.replywise.model.ActivityData;
import com.replywise.model.ContactInsight;
import com.replywise.model.DashboardData;
import com.replywise.model.ImportResult;
import com.replywise.model.SummaryStats;
import com.replywise.service.AnalyticsService;
import com.replywise.service.ImessageExtractorService;
import com.replywise.service.MessageImportService;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class DashboardController {
    private final AnalyticsService analyticsService;
    private final MessageImportService messageImportService;
    private final ImessageExtractorService imessageExtractorService;

    public DashboardController(
        AnalyticsService analyticsService,
        MessageImportService messageImportService,
        ImessageExtractorService imessageExtractorService
    ) {
        this.analyticsService = analyticsService;
        this.messageImportService = messageImportService;
        this.imessageExtractorService = imessageExtractorService;
    }

    @GetMapping("/api/dashboard")
    public DashboardData dashboard() {
        return analyticsService.dashboard();
    }

    @GetMapping("/api/summary")
    public SummaryStats summary() {
        return analyticsService.dashboard().summary();
    }

    @GetMapping("/api/contacts")
    public List<ContactInsight> contacts(@RequestParam(defaultValue = "") String query) {
        return analyticsService.contacts(query);
    }

    @GetMapping("/api/contacts/{id}")
    public ContactInsight contact(@PathVariable String id) {
        return analyticsService.contact(id);
    }

    @GetMapping("/api/activity")
    public ActivityData activity() {
        return analyticsService.dashboard().activity();
    }

    @PostMapping(value = "/api/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importMessages(@RequestParam("file") MultipartFile file) throws IOException {
        var messages = messageImportService.parse(file.getOriginalFilename(), file.getBytes());
        return analyticsService.replaceMessages(file.getOriginalFilename(), messages);
    }

    @PostMapping("/api/import/imessage")
    public ImportResult importImessage(@RequestParam(required = false) String dbPath)
        throws SQLException, IOException {
        var source = resolveDbPath(dbPath);
        var messages = imessageExtractorService.extract(source);
        return analyticsService.replaceMessages(source.toString(), messages);
    }

    @GetMapping(value = "/api/export/imessage.csv", produces = "text/csv")
    public ResponseEntity<String> exportImessageCsv(@RequestParam(required = false) String dbPath)
        throws SQLException, IOException {
        var source = resolveDbPath(dbPath);
        var csv = imessageExtractorService.exportCsv(source);

        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("replywise-imessage-timing.csv").build().toString()
            )
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv);
    }

    @PostMapping("/api/import/demo")
    public ImportResult loadDemoData() {
        analyticsService.resetDemoData();
        return new ImportResult(
            "demo",
            analyticsService.dashboard().contacts().size(),
            "Demo data loaded from the Spring Boot analytics service."
        );
    }

    @ExceptionHandler({IOException.class, SQLException.class, IllegalArgumentException.class})
    public ResponseEntity<ImportResult> handleImportError(Exception exception) {
        return ResponseEntity.badRequest().body(new ImportResult(
            "iMessage",
            0,
            exception.getMessage()
        ));
    }

    private Path resolveDbPath(String dbPath) {
        if (dbPath == null || dbPath.isBlank()) {
            return imessageExtractorService.defaultDatabasePath();
        }
        if (dbPath.equals("~")) {
            return Path.of(System.getProperty("user.home"));
        }
        if (dbPath.startsWith("~/")) {
            return Path.of(System.getProperty("user.home"), dbPath.substring(2));
        }
        return Path.of(dbPath);
    }
}
