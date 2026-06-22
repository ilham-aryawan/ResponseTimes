package com.replywise.service;

import com.replywise.model.RawMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.springframework.stereotype.Service;
import org.sqlite.SQLiteConfig;

@Service
public class ImessageExtractorService {
    private static final Instant APPLE_EPOCH = Instant.parse("2001-01-01T00:00:00Z");
    private static final String MESSAGE_QUERY = """
        SELECT
            m.ROWID AS row_id,
            m.date AS message_date,
            m.is_from_me AS is_from_me,
            COALESCE(
                NULLIF(c.display_name, ''),
                NULLIF((
                    SELECT group_concat(h2.id, ', ')
                    FROM chat_handle_join chj2
                    JOIN handle h2 ON h2.ROWID = chj2.handle_id
                    WHERE chj2.chat_id = c.ROWID
                ), ''),
                h.id,
                'Unknown conversation'
            ) AS contact
        FROM message m
        LEFT JOIN chat_message_join cmj ON cmj.message_id = m.ROWID
        LEFT JOIN chat c ON c.ROWID = cmj.chat_id
        LEFT JOIN handle h ON h.ROWID = m.handle_id
        WHERE m.date IS NOT NULL
          AND m.date > 0
        ORDER BY COALESCE(c.ROWID, -1), m.date
        """;

    public Path defaultDatabasePath() {
        return Path.of(System.getProperty("user.home"), "Library", "Messages", "chat.db");
    }

    public List<RawMessage> extract(Path databasePath) throws IOException, SQLException {
        var dbPath = validateDatabasePath(databasePath);
        var config = new SQLiteConfig();
        config.setReadOnly(true);
        var properties = new Properties();
        properties.putAll(config.toProperties());

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath, properties);
             var statement = connection.prepareStatement(MESSAGE_QUERY);
             var rows = statement.executeQuery()) {
            var messages = new ArrayList<RawMessage>();
            while (rows.next()) {
                messages.add(new RawMessage(
                    rows.getString("contact"),
                    appleTimestampToInstant(rows.getLong("message_date")),
                    rows.getInt("is_from_me") == 1
                ));
            }
            return messages;
        } catch (SQLException exception) {
            throw new SQLException(
                "Could not read iMessage database. Give your terminal Full Disk Access, then restart Spring Boot. "
                    + "Original error: " + exception.getMessage(),
                exception
            );
        }
    }

    public String exportCsv(Path databasePath) throws IOException, SQLException {
        var builder = new StringBuilder("contact,timestamp,direction\n");
        for (var message : extract(databasePath)) {
            builder
                .append(csv(message.contact()))
                .append(',')
                .append(message.timestamp())
                .append(',')
                .append(message.fromMe() ? "sent" : "received")
                .append('\n');
        }
        return builder.toString();
    }

    private Path validateDatabasePath(Path databasePath) throws IOException {
        var dbPath = databasePath.toAbsolutePath().normalize();
        if (!Files.exists(dbPath)) {
            throw new IOException("Could not find iMessage database at " + dbPath + ".");
        }
        if (!Files.isRegularFile(dbPath)) {
            throw new IOException("The iMessage database path is not a file: " + dbPath + ".");
        }
        if (!Files.isReadable(dbPath)) {
            throw new IOException(
                "Cannot read " + dbPath + ". Give your terminal Full Disk Access, then restart Spring Boot."
            );
        }
        return dbPath;
    }

    private Instant appleTimestampToInstant(long rawDate) {
        var absolute = Math.abs(rawDate);
        if (absolute > 10_000_000_000_000_000L) {
            var seconds = rawDate / 1_000_000_000L;
            var nanos = rawDate % 1_000_000_000L;
            return APPLE_EPOCH.plusSeconds(seconds).plusNanos(nanos);
        }
        if (absolute > 10_000_000_000_000L) {
            var seconds = rawDate / 1_000_000L;
            var nanos = (rawDate % 1_000_000L) * 1_000L;
            return APPLE_EPOCH.plusSeconds(seconds).plusNanos(nanos);
        }
        if (absolute > 10_000_000_000L) {
            return APPLE_EPOCH.plusMillis(rawDate);
        }
        return APPLE_EPOCH.plusSeconds(rawDate);
    }

    private String csv(String value) {
        var escaped = value == null ? "" : value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return '"' + escaped + '"';
        }
        return escaped;
    }
}
