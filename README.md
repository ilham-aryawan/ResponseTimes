# Replywise

A polished, responsive dashboard for analyzing message response times across your contacts.

## Run locally

The app is now served by a local Spring Boot backend on port `3000`:

```bash
mvn spring-boot:run
```

Then open `http://localhost:3000`.

The frontend is in `src/main/resources/static`, so Spring Boot serves the dashboard and API from the same origin.

## API

- `GET /api/dashboard` returns the full dashboard payload
- `GET /api/summary` returns summary metrics
- `GET /api/contacts` returns the contact leaderboard
- `GET /api/contacts/{id}` returns one contact drill-down
- `GET /api/activity` returns chart and heatmap data
- `POST /api/import` accepts a local CSV or JSON file upload
- `POST /api/import/imessage` imports timing metadata from `~/Library/Messages/chat.db`
- `GET /api/export/imessage.csv` downloads a privacy-safe CSV from `chat.db`
- `POST /api/import/demo` reloads the demo dataset

## Import From iMessage

Replywise can read the local macOS Messages database directly when Spring Boot is running on your Mac.

1. Open `System Settings > Privacy & Security > Full Disk Access`.
2. Add your terminal app, such as Terminal, iTerm, or the app you use to run Maven.
3. Restart the terminal app.
4. Run:

```bash
mvn spring-boot:run
```

5. Open `http://localhost:3000`, click **Import messages**, then choose **Import from iMessage**.

This reads `~/Library/Messages/chat.db` in read-only mode and keeps only:

- Contact or conversation label
- Timestamp
- Direction, either `sent` or `received`

Message text and attachments are not imported.

## Import Format

Replywise only keeps timing metadata. Message bodies are ignored.

CSV uploads should include these columns:

```csv
contact,timestamp,direction
Maya Chen,2026-06-01T18:00:00Z,sent
Maya Chen,2026-06-01T18:04:00Z,received
```

JSON uploads can use equivalent fields:

```json
[
  { "contact": "Maya Chen", "timestamp": "2026-06-01T18:00:00Z", "direction": "sent" },
  { "contact": "Maya Chen", "timestamp": "2026-06-01T18:04:00Z", "direction": "received" }
]
```

## Features

- Response-time summary metrics and comparison trends
- Interactive 7-day, 30-day, and 3-month charts
- Searchable contact response leaderboard
- Per-contact analytics drawer
- Weekly message activity heatmap
- Spring Boot REST API for dashboard data
- Message import flow with drag-and-drop upload support
- Privacy-first parsing that ignores message content
- Responsive layout for desktop, tablet, and mobile
