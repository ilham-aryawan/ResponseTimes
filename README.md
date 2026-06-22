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
- `POST /api/import` accepts a local CSV, JSON, vCard `.vcf`, or uploaded `chat.db` file
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

If macOS will not let the app read `~/Library/Messages/chat.db` directly, click **Import messages** and use the file picker instead. Choose:

```text
/Users/your-name/Library/Messages/chat.db
```

In Finder's file picker, press `Command + Shift + G`, paste `~/Library/Messages`, and select `chat.db`.

This reads `~/Library/Messages/chat.db` in read-only mode and keeps only:

- Contact or conversation label
- Timestamp
- Direction, either `sent` or `received`

Message text and attachments are not imported.

## Show Contact Names

`chat.db` often stores phone numbers or email handles, not the names from your Contacts app. To show names:

1. Open the macOS **Contacts** app.
2. Select the contacts you want, or press `Command + A` for all contacts.
3. Choose **File > Export > Export vCard...**.
4. Save the `.vcf` file.
5. In Replywise, click **Import messages** and upload the `.vcf` file.
6. Import `chat.db` again, or upload the `.vcf` after `chat.db` to relabel the current dashboard.

Replywise uses the vCard phone numbers and emails only to rename handles like `+15555550100` to contact names.

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
