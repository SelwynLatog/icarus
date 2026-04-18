package engine;

// Predictive analytics engine for admin dashboard
// Computes aggregates, trends, and violation patterns from decision log data

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;
import model.Item;
import model.Student;
import storage.dao.ItemDAO;

public class ItemAnalytics {

    // Time range filter constants
    public static final int RANGE_DAY   = 0;
    public static final int RANGE_WEEK  = 1;
    public static final int RANGE_MONTH = 2;
    public static final int RANGE_YEAR  = 3;
    public static final int RANGE_ALL   = 4;

    // Trend direction returned per category
    public enum Trend { UP, DOWN, STABLE, NEW }

    // Container for a single category trend result
    public static class TrendResult {
        public final String category;
        public final int    currentCount;
        public final int    previousCount;
        public final Trend  trend;
        public final double changePercent;

        public TrendResult(String category, int current, int previous) {
            this.category     = category;
            this.currentCount = current;
            this.previousCount = previous;
            this.changePercent = previous == 0
                ? (current > 0 ? 100.0 : 0.0)
                : ((double)(current - previous) / previous) * 100.0;

            if (previous == 0 && current > 0) this.trend = Trend.NEW;
            else if (changePercent >  10.0)   this.trend = Trend.UP;
            else if (changePercent < -10.0)   this.trend = Trend.DOWN;
            else                               this.trend = Trend.STABLE;
        }
    }

    // Container for the full analytics snapshot passed to the UI
    public static class AnalyticsSnapshot {
        public final LocalDateTime from;
        public final LocalDateTime to;

        // Item counts
        public int totalItems;
        public int totalHeld;
        public int totalReleased;

        // Decision counts
        public int totalAllow;
        public int totalDisallow;
        public int totalConditional;

        // Threat counts
        public int criticalCount;
        public int highCount;
        public int mediumCount;
        public int lowCount;

        // Breakdowns
        public Map<String, Integer> categoryCount    = new LinkedHashMap<>();
        public Map<String, Integer> threatMap        = new LinkedHashMap<>();
        public Map<String, Integer> decisionMap      = new LinkedHashMap<>();
        public Map<String, Integer> violatorMap      = new LinkedHashMap<>();

        // Predictive
        public List<TrendResult>    categoryTrends   = new ArrayList<>();
        public String               peakDay          = "N/A";
        public String               peakHour         = "N/A";
        public String               mostFlaggedCat   = "N/A";
        public String               prediction       = "";

        public AnalyticsSnapshot(LocalDateTime from, LocalDateTime to) {
            this.from = from;
            this.to   = to;
        }
    }

    // Returns the time range bounds for a given RANGE_ constant.
    public static LocalDateTime[] getRangeBounds(int range) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = switch (range) {
            case RANGE_DAY   -> now.minusDays(1);
            case RANGE_WEEK  -> now.minusWeeks(1);
            case RANGE_MONTH -> now.minusMonths(1);
            case RANGE_YEAR  -> now.minusYears(1);
            default          -> LocalDateTime.of(2000, 1, 1, 0, 0);
        };
        return new LocalDateTime[]{ from, now };
    }

    // Main entry point that computes the full snapshot for the given range.
    public static AnalyticsSnapshot compute(
            int range,
            List<Map<String, Object>> allLogs,
            List<Item> allItems,
            List<Student> allStudents,
            ItemDAO itemDAO
    ) {
        LocalDateTime[] bounds = getRangeBounds(range);
        LocalDateTime from = bounds[0];
        LocalDateTime to   = bounds[1];

        // Filter logs to current period
        List<Map<String, Object>> logs = allLogs.stream()
            .filter(log -> {
                LocalDateTime ts = (LocalDateTime) log.get("evaluated_at");
                return !ts.isBefore(from) && !ts.isAfter(to);
            })
            .collect(Collectors.toList());

        // Filter items to current period
        List<Item> items = allItems.stream()
            .filter(item -> !item.getTimestamp().isBefore(from) && !item.getTimestamp().isAfter(to))
            .collect(Collectors.toList());

        AnalyticsSnapshot snap = new AnalyticsSnapshot(from, to);

        // Item status counts
        snap.totalItems = items.size();
        for (Item item : items) {
            switch (item.getStatus()) {
                case HELD     -> snap.totalHeld++;
                case RELEASED -> snap.totalReleased++;
            }
            snap.categoryCount.merge(item.getPrimaryCategory().name(), 1, Integer::sum);
        }

        // Decision and threat counts from logs
        for (Map<String, Object> log : logs) {
            String dec = (String) log.get("decision");
            String thr = (String) log.get("threat_level");
            switch (dec) {
                case "ALLOW"       -> snap.totalAllow++;
                case "DISALLOW"    -> snap.totalDisallow++;
                case "CONDITIONAL" -> snap.totalConditional++;
            }
            switch (thr) {
                case "CRITICAL" -> snap.criticalCount++;
                case "HIGH"     -> snap.highCount++;
                case "MEDIUM"   -> snap.mediumCount++;
                case "LOW"      -> snap.lowCount++;
            }
        }

        // Structured maps for charts
        snap.threatMap.put("CRITICAL", snap.criticalCount);
        snap.threatMap.put("HIGH",     snap.highCount);
        snap.threatMap.put("MEDIUM",   snap.mediumCount);
        snap.threatMap.put("LOW",      snap.lowCount);

        snap.decisionMap.put("ALLOW",       snap.totalAllow);
        snap.decisionMap.put("CONDITIONAL", snap.totalConditional);
        snap.decisionMap.put("DISALLOW",    snap.totalDisallow);

        // Violator counts
        for (Student s : allStudents) {
            try {
                List<Item> studentItems = itemDAO.findItemsByStudentId(s.getStudentId());
                long violations = studentItems.stream()
                    .filter(i -> !i.getTimestamp().isBefore(from) && !i.getTimestamp().isAfter(to))
                    .count();
                if (violations > 0)
                    snap.violatorMap.put(s.getFullName() + " (" + s.getStudentId() + ")", (int) violations);
            } catch (Exception ignored) {}
        }

        // Peak day of week
        Map<DayOfWeek, Long> dayCount = logs.stream()
            .collect(Collectors.groupingBy(
                log -> ((LocalDateTime) log.get("evaluated_at")).getDayOfWeek(),
                Collectors.counting()
            ));
        dayCount.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .ifPresent(e -> snap.peakDay = e.getKey()
                .getDisplayName(TextStyle.FULL, Locale.ENGLISH));

        // Peak hour of day
        Map<Integer, Long> hourCount = logs.stream()
            .collect(Collectors.groupingBy(
                log -> ((LocalDateTime) log.get("evaluated_at")).getHour(),
                Collectors.counting()
            ));
        hourCount.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .ifPresent(e -> snap.peakHour = String.format("%02d:00", e.getKey()));

        // Most flagged category
        snap.mostFlaggedCat = snap.categoryCount.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("N/A");

        // Trend detection compare current period vs previous period of same length
        snap.categoryTrends = computeTrends(range, allLogs, allItems);

        // Prediction string
        snap.prediction = generatePrediction(snap);

        return snap;
    }

    // Compares current period category counts vs the previous period of equal length.
    private static List<TrendResult> computeTrends(
            int range,
            List<Map<String, Object>> allLogs,
            List<Item> allItems
    ) {
        LocalDateTime[] current  = getRangeBounds(range);
        LocalDateTime   from     = current[0];
        LocalDateTime   to       = current[1];
        long            duration = java.time.Duration.between(from, to).toSeconds();
        LocalDateTime   prevFrom = from.minusSeconds(duration);
        LocalDateTime   prevTo   = from;

        // Category counts for current period
        Map<String, Integer> currentCounts = allItems.stream()
            .filter(i -> !i.getTimestamp().isBefore(from) && !i.getTimestamp().isAfter(to))
            .collect(Collectors.groupingBy(
                i -> i.getPrimaryCategory().name(),
                Collectors.summingInt(i -> 1)
            ));

        // Category counts for previous period
        Map<String, Integer> previousCounts = allItems.stream()
            .filter(i -> !i.getTimestamp().isBefore(prevFrom) && !i.getTimestamp().isAfter(prevTo))
            .collect(Collectors.groupingBy(
                i -> i.getPrimaryCategory().name(),
                Collectors.summingInt(i -> 1)
            ));

        // Build trend results for all categories seen in either period
        Set<String> allCategories = new HashSet<>();
        allCategories.addAll(currentCounts.keySet());
        allCategories.addAll(previousCounts.keySet());

        return allCategories.stream()
            .map(cat -> new TrendResult(
                cat,
                currentCounts.getOrDefault(cat, 0),
                previousCounts.getOrDefault(cat, 0)
            ))
            .sorted(Comparator.comparingInt((TrendResult t) -> t.currentCount).reversed())
            .collect(Collectors.toList());
    }

    // Generates a plain text prediction summary based on the snapshot
    private static String generatePrediction(AnalyticsSnapshot snap) {
        if (snap.totalItems == 0) return "Insufficient data for predictions.";

        List<String> lines = new ArrayList<>();

        // Flag upward trends
        for (TrendResult t : snap.categoryTrends) {
            if (t.trend == Trend.UP) {
                lines.add(String.format("%s violations up %.0f%% vs previous period.",
                    t.category, t.changePercent));
            }
            if (t.trend == Trend.NEW) {
                lines.add(String.format("%s: new violation category detected this period.",
                    t.category));
            }
        }

        // Peak timing insight
        if (!"N/A".equals(snap.peakDay))
            lines.add("Most incidents occur on " + snap.peakDay + "s at " + snap.peakHour + ".");

        // High threat warning
        if (snap.criticalCount > 0)
            lines.add("CRITICAL threat items detected: " + snap.criticalCount
                + ". Recommend security review.");

        if (snap.highCount > snap.mediumCount + snap.lowCount && snap.highCount > 0)
            lines.add("High-threat items dominate this period. Consider increased screening.");

        // Repeat violator warning
        long repeatViolators = snap.violatorMap.values().stream().filter(v -> v > 1).count();
        if (repeatViolators > 0)
            lines.add(repeatViolators + " student(s) with multiple violations this period.");

        if (lines.isEmpty()) return "No significant patterns detected in this period.";

        return String.join("\n", lines);
    }
}