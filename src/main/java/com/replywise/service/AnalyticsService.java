package com.replywise.service;

import com.replywise.model.ActivityData;
import com.replywise.model.ChartPeriod;
import com.replywise.model.ContactInsight;
import com.replywise.model.DashboardData;
import com.replywise.model.HeatmapData;
import com.replywise.model.ImportResult;
import com.replywise.model.RawMessage;
import com.replywise.model.SummaryStats;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {
    private static final List<String> AVATARS = List.of(
        "avatar-maya", "avatar-noah", "avatar-elena", "avatar-jordan",
        "avatar-liam", "avatar-priya", "avatar-alex"
    );
    private static final ZoneId DISPLAY_ZONE = ZoneId.systemDefault();

    private final AtomicReference<DashboardData> dashboard = new AtomicReference<>(demoDashboard());
    private final AtomicReference<List<RawMessage>> importedMessages = new AtomicReference<>(List.of());

    public DashboardData dashboard() {
        return dashboard.get();
    }

    public List<ContactInsight> contacts(String query) {
        var normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return dashboard.get().contacts().stream()
            .filter(contact -> normalized.isBlank()
                || contact.name().toLowerCase(Locale.ROOT).contains(normalized)
                || contact.handle().toLowerCase(Locale.ROOT).contains(normalized))
            .toList();
    }

    public ContactInsight contact(String id) {
        return dashboard.get().contacts().stream()
            .filter(contact -> contact.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("No contact found for id " + id));
    }

    public ImportResult replaceMessages(String source, List<RawMessage> messages) {
        if (messages.isEmpty()) {
            resetDemoData();
            return new ImportResult(
                Objects.toString(source, "upload"),
                dashboard.get().contacts().size(),
                "No timing rows were found, so Replywise kept the demo dataset."
            );
        }

        importedMessages.set(List.copyOf(messages));
        var nextContacts = analyzeContacts(messages);
        var nextDashboard = new DashboardData(
            summarize(nextContacts),
            nextContacts,
            demoActivity()
        );
        dashboard.set(nextDashboard);

        return new ImportResult(
            Objects.toString(source, "upload"),
            nextContacts.size(),
            "Imported " + messages.size() + " timing rows. Message bodies were ignored."
        );
    }

    public int relabelImportedMessages(UnaryOperator<String> labelResolver) {
        var messages = importedMessages.get();
        if (messages.isEmpty()) {
            return 0;
        }

        var relabeled = messages.stream()
            .map(message -> new RawMessage(
                labelResolver.apply(message.contact()),
                message.timestamp(),
                message.fromMe()
            ))
            .toList();
        var nextContacts = analyzeContacts(relabeled);
        dashboard.set(new DashboardData(
            summarize(nextContacts),
            nextContacts,
            dashboard.get().activity()
        ));
        importedMessages.set(relabeled);
        return nextContacts.size();
    }

    public void resetDemoData() {
        importedMessages.set(List.of());
        dashboard.set(demoDashboard());
    }

    private List<ContactInsight> analyzeContacts(List<RawMessage> messages) {
        var grouped = messages.stream()
            .filter(message -> !message.contact().isBlank())
            .collect(Collectors.groupingBy(RawMessage::contact));

        var contacts = new ArrayList<ContactInsight>();
        var avatarIndex = 0;
        for (var entry : grouped.entrySet()) {
            var contactMessages = entry.getValue().stream()
                .sorted(Comparator.comparing(RawMessage::timestamp))
                .toList();
            var responseMinutes = responseMinutes(contactMessages);
            var medianMinutes = median(responseMinutes);
            var sentByMe = contactMessages.stream().filter(RawMessage::fromMe).count();
            var responseRate = sentByMe == 0
                ? "0%"
                : Math.min(100, Math.round((responseMinutes.size() * 100.0) / sentByMe)) + "%";
            var bestTime = bestReplyWindow(contactMessages);
            var formattedTime = formatDuration(medianMinutes);
            var latest = contactMessages.get(contactMessages.size() - 1).timestamp();
            var daysAgo = Math.max(1, Duration.between(latest, Instant.now()).toDays() + 1);
            var name = entry.getKey();

            contacts.add(new ContactInsight(
                slug(name),
                name,
                initials(name),
                "Imported · " + contactMessages.size() + " messages",
                formattedTime.value(),
                formattedTime.unit(),
                AVATARS.get(avatarIndex % AVATARS.size()),
                responseRate,
                bestTime,
                contactMessages.size(),
                daysAgo + (daysAgo == 1 ? " day" : " days"),
                name + " usually replies around " + bestTime + " with a median response of "
                    + formattedTime.display() + " " + formattedTime.unit() + "."
            ));
            avatarIndex++;
        }

        return contacts.stream()
            .sorted(Comparator.comparingDouble(contact -> toMinutes(contact.time(), contact.unit())))
            .toList();
    }

    private List<Long> responseMinutes(List<RawMessage> messages) {
        var intervals = new ArrayList<Long>();
        RawMessage previous = null;
        for (var message : messages) {
            if (previous != null && previous.fromMe() && !message.fromMe()) {
                var minutes = Duration.between(previous.timestamp(), message.timestamp()).toMinutes();
                if (minutes > 0 && minutes <= 7 * 24 * 60) {
                    intervals.add(minutes);
                }
            }
            previous = message;
        }
        return intervals;
    }

    private long median(List<Long> values) {
        if (values.isEmpty()) {
            return 0;
        }
        var sorted = values.stream().sorted().toList();
        var middle = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return Math.round((sorted.get(middle - 1) + sorted.get(middle)) / 2.0);
        }
        return sorted.get(middle);
    }

    private SummaryStats summarize(List<ContactInsight> contacts) {
        if (contacts.isEmpty()) {
            return new SummaryStats(
                "0 min", "0%", "0", "No contacts analyzed",
                "None", "Import messages to begin", "Not enough data", "Upload a timing export"
            );
        }

        var medianMinutes = median(contacts.stream()
            .map(contact -> Math.round(toMinutes(contact.time(), contact.unit())))
            .toList());
        var formattedMedian = formatDuration(medianMinutes);
        var fastest = contacts.get(0);
        var totalMessages = contacts.stream().mapToInt(ContactInsight::conversations).sum();

        return new SummaryStats(
            formattedMedian.display() + " " + formattedMedian.unit(),
            "Live",
            String.format(Locale.US, "%,d", totalMessages),
            "Across " + contacts.size() + " active contacts",
            firstName(fastest.name()),
            displayNumber(fastest.time()) + " " + fastest.unit() + " median reply",
            "6:30 PM",
            "Replies trend quickest in the evening"
        );
    }

    private String bestReplyWindow(List<RawMessage> messages) {
        var counts = new HashMap<Integer, Integer>();
        messages.stream()
            .filter(message -> !message.fromMe())
            .forEach(message -> {
                var hour = message.timestamp().atZone(DISPLAY_ZONE).getHour();
                var bucket = (hour / 2) * 2;
                counts.merge(bucket, 1, Integer::sum);
            });

        var bestHour = counts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(18);
        return formatHour(bestHour) + "–" + formatHour((bestHour + 2) % 24);
    }

    private String formatHour(int hour) {
        var suffix = hour < 12 ? "AM" : "PM";
        var display = hour % 12;
        if (display == 0) {
            display = 12;
        }
        return display + " " + suffix;
    }

    private DurationLabel formatDuration(long minutes) {
        if (minutes < 60) {
            var value = Math.max(1, minutes);
            return new DurationLabel(value, Long.toString(value), "min");
        }
        var hours = Math.round((minutes / 60.0) * 10.0) / 10.0;
        var display = hours == Math.rint(hours)
            ? Long.toString(Math.round(hours))
            : Double.toString(hours);
        return new DurationLabel(hours, display, "hr");
    }

    private double toMinutes(double value, String unit) {
        return "hr".equals(unit) ? value * 60 : value;
    }

    private String displayNumber(double value) {
        return value == Math.rint(value)
            ? Long.toString(Math.round(value))
            : Double.toString(value);
    }

    private String firstName(String name) {
        var parts = name.split("\\s+");
        return parts.length == 0 ? name : parts[0];
    }

    private String initials(String name) {
        return java.util.Arrays.stream(name.trim().split("\\s+"))
            .filter(part -> !part.isBlank())
            .limit(2)
            .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT))
            .collect(Collectors.joining());
    }

    private String slug(String value) {
        var slug = value.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "contact" : slug;
    }

    private DashboardData demoDashboard() {
        var contacts = List.of(
            new ContactInsight("maya-chen", "Maya Chen", "MC", "Close friend · 186 messages", 4, "min", "avatar-maya", "96%", "6–8 PM", 38, "12 days", "Maya is fastest in the evening and usually replies within 2 minutes on Wednesdays."),
            new ContactInsight("noah-williams", "Noah Williams", "NW", "Work · 142 messages", 11, "min", "avatar-noah", "91%", "9–11 AM", 31, "6 days", "Morning messages get the quickest response. Mondays tend to be slower than the rest of the week."),
            new ContactInsight("elena-rossi", "Elena Rossi", "ER", "Family · 118 messages", 18, "min", "avatar-elena", "89%", "7–9 PM", 26, "9 days", "Elena is reliably responsive after dinner, with very little variation between weekdays."),
            new ContactInsight("jordan-lee", "Jordan Lee", "JL", "Friend · 97 messages", 27, "min", "avatar-jordan", "84%", "12–2 PM", 24, "4 days", "Jordan often replies around lunch. Weekend response times are about 14 minutes slower."),
            new ContactInsight("liam-brooks", "Liam Brooks", "LB", "Work · 84 messages", 42, "min", "avatar-liam", "76%", "3–5 PM", 19, "3 days", "Liam batches replies in the late afternoon. Messages sent before noon often wait until after 3 PM."),
            new ContactInsight("priya-kapoor", "Priya Kapoor", "PK", "Friend · 72 messages", 1.2, "hr", "avatar-priya", "71%", "8–10 PM", 17, "2 days", "Priya is most active later at night. Her reply window is more predictable on weekends."),
            new ContactInsight("alex-morgan", "Alex Morgan", "AM", "Community · 58 messages", 2.4, "hr", "avatar-alex", "63%", "10 AM–12 PM", 12, "1 day", "Alex tends to respond in focused blocks. Mid-morning messages have the best chance of a same-hour reply.")
        );
        return new DashboardData(
            new SummaryStats("24 min", "18%", "1,284", "Across 42 active contacts", "Maya", "4 min median reply", "6:30 PM", "Replies are 31% quicker in the evening"),
            contacts,
            demoActivity()
        );
    }

    private ActivityData demoActivity() {
        return new ActivityData(
            Map.of(
                "7", new ChartPeriod(
                    List.of(21, 30, 18, 26, 15, 32, 24),
                    List.of(34, 38, 27, 31, 25, 40, 36),
                    List.of("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed"),
                    "24 min"
                ),
                "30", new ChartPeriod(
                    List.of(42, 36, 47, 30, 32, 24, 38, 27, 20, 24),
                    List.of(48, 45, 44, 39, 42, 37, 41, 35, 33, 36),
                    List.of("May 12", "15", "18", "21", "24", "27", "30", "Jun 2", "5", "8"),
                    "24 min"
                ),
                "90", new ChartPeriod(
                    List.of(52, 49, 44, 48, 39, 42, 35, 33, 29, 31, 24, 26),
                    List.of(55, 51, 53, 47, 48, 43, 45, 39, 40, 36, 35, 34),
                    List.of("Mar", "", "Late Mar", "", "Apr", "", "Late Apr", "", "May", "", "Late May", "Jun"),
                    "32 min"
                )
            ),
            new HeatmapData(
                List.of(
                    List.of(0, 0, 1, 1, 2, 3, 4, 2),
                    List.of(0, 1, 1, 2, 2, 3, 4, 3),
                    List.of(0, 1, 2, 2, 3, 4, 5, 4),
                    List.of(0, 1, 2, 3, 3, 4, 5, 4),
                    List.of(0, 1, 1, 2, 3, 3, 4, 5),
                    List.of(0, 0, 1, 1, 2, 3, 4, 4),
                    List.of(0, 0, 1, 2, 2, 3, 4, 3)
                ),
                java.time.DayOfWeek.values().length == 7
                    ? List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    : List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
                List.of("8a", "10a", "12p", "2p", "4p", "6p", "8p", "10p")
            )
        );
    }

    private record DurationLabel(double value, String display, String unit) {
    }
}
