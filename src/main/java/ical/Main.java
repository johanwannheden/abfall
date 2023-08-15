package ical;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

enum Abfall {
    GRUENABFUHR("Grünabfuhr"), PAPIER("Papier"), GLAS("Glas"), DEPONIE("Deponie"), METALL("Metall"), PLASTIK("Plastik");

    private final String label;

    Abfall(String label) {
        this.label = label;
    }

    String getLabel() {
        return label;
    }

    boolean periodicRuleApplies(Month month) {
        if (this == GRUENABFUHR) {
            return month.getValue() > Month.FEBRUARY.getValue() && month.getValue() < Month.DECEMBER.getValue();
        }
        return false;
    }
}

public class Main {

    // https://www.muri-guemligen.ch/fileadmin/muriguemligench/00_Headerbilder/WEB_Gem_Muri_Entsorgungskalender2023_DE.pdf
    private static final String SOURCE_PDF = "https://tinyurl.com/mrjz4ktr";

    private static final Map<Abfall, String> ABFALL_TO_FILE = Map.of(//
            Abfall.GRUENABFUHR, "gruenabfuhr.properties",//
            Abfall.PAPIER, "papier.properties",//
            Abfall.METALL, "metall.properties",//
            Abfall.GLAS, "glas.properties",//
            Abfall.DEPONIE, "deponie.properties",//
            Abfall.PLASTIK, "plastik.properties"//
    );
    private static int year;

    public static void main(String[] args) throws IOException {
        year = LocalDate.now().getYear();
        var path = Files.createTempFile("abfall-", ".ics");

        List<String> events = Arrays.stream(Abfall.values()).flatMap(Main::getAbfallEvents).toList();
        var calendar = generateCalendar(events);

        Files.writeString(path, calendar);
//        System.out.println(calendar);
//        System.out.println(LocalTime.now());
        System.out.println(path);
        Desktop.getDesktop().open(path.toFile());
    }

    static Stream<String> getAbfallEvents(Abfall kind) {
        var datesForAbfall = getAbfallDates(kind);
        if (datesForAbfall.isEmpty()) {
            throw new IllegalStateException(String.format("No dates for %s could be found", kind));
        }
        return datesForAbfall.stream().map(it -> abfallEventForDate(it, kind)).map(Event::serialize);
    }

    static Set<LocalDate> getAbfallDates(Abfall kind) {
        try {
            var p = new Properties();
            p.load(Main.class.getResourceAsStream("/" + ABFALL_TO_FILE.get(kind)));
            return p.entrySet()
                    .stream()
                    .flatMap(e -> getDate(kind, Month.of(Integer.parseInt((String) e.getKey())), (String) e.getValue()).stream())
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new RuntimeException("Failed reading Abfall dates", e);
        }
    }

    private static Set<LocalDate> getDate(Abfall kind, Month month, String listOfDays) {
        if (kind.periodicRuleApplies(month)) {
            var result = new HashSet<LocalDate>();
            var current = LocalDate.of(year, month.getValue(), 1);
            while (current.isBefore(LocalDate.of(year, month.getValue() + 1, 1))) {
                if (current.getDayOfWeek() == DayOfWeek.TUESDAY) {
                    result.add(current);
                    current = current.plusDays(7);
                } else {
                    current = current.plusDays(1);
                }
            }
            return result;
        }
        var days = Arrays.stream(listOfDays.split(",")).distinct()
                .filter(Predicate.not(String::isEmpty))
                .map(Integer::parseInt).toList();
        return days.stream().map(d -> LocalDate.of(year, month.getValue(), d)).collect(Collectors.toSet());
    }

    static String generateCalendar(List<String> events) {
        var header = """
                BEGIN:VCALENDAR
                PRODID:-//xyz Corp//NONSGML PDA Calendar Version 1.0//EN
                VERSION:2.0
                """;
        var footer = "END:VCALENDAR";
        var calendar = String.format("%s%s%n%s", header, String.join(System.getProperty("line.separator"), events), footer);
        return calendar.replaceAll(System.getProperty("line.separator"), "\r\n"); // must be CRLF
    }

    static Event abfallEventForDate(LocalDate date, Abfall kind) {
        System.out.printf("%s on %s%n", kind, date);
        var dtstamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + 'T' + LocalTime.now()
                .format(DateTimeFormatter.ofPattern("HHmmss")) + 'Z';
        var dtstart = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        return new Event(kind.name() + '-' + dtstart, // uid
                SOURCE_PDF, // desc
                dtstamp, // dtstamp
                dtstart, // start
                "P1D", // duration
                kind.getLabel() // label
        );
    }
}

record Event(String uid, String description, String dtstamp, String dtstart, String duration, String summary) {
    public String serialize() {
        StringJoiner joiner = new StringJoiner(System.getProperty("line.separator"))//
                .add("BEGIN:VEVENT")
                .add("UID:" + uid)
                .add("DTSTAMP:" + dtstamp)
                .add("DTSTART:" + dtstart)
                .add("DURATION:" + duration)
                .add("SUMMARY:" + summary)
                .add("DESCRIPTION:" + description);
        return joiner.add("END:VEVENT").toString();
    }
}
