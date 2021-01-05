package ical;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Builder;
import lombok.Data;

enum Abfall {
    GRUENABFUHR("Grünabfuhr"), PAPIER("Papier"), GLAS("Glas"), DEPONIE("Deponie"), METALL("Metall");

    private final String label;

    Abfall(String label) {
        this.label = label;
    }

    String getLabel() {
        return label;
    }
}

public class Main {

    //"http://www.muri-guemligen.ch/fileadmin/muriguemligench/02_Verwaltung/Bauverwaltung/Umwelt/Energiefachstelle/Entsorgungskalender_2021_Deutsch.pdf";
    private static final String SOURCE_PDF = "https://tinyurl.com/y35gz9rm";

    private static final Map<Abfall, String> ABFALL_TO_FILE = Map.of(//
            Abfall.GRUENABFUHR, "gruenabfuhr.properties",//
            Abfall.PAPIER, "papier.properties",//
            Abfall.METALL, "metall.properties",//
            Abfall.GLAS, "glas.properties",//
            Abfall.DEPONIE, "deponie.properties"//
    );

    public static void main(String[] args) throws IOException {
        var path = Files.createTempFile("abfall-", ".ics");

        List<String> events = Arrays.stream(Abfall.values()).flatMap(Main::getAbfallEvents).collect(Collectors.toList());
        var calendar = generateCalendar(events);

        Files.writeString(path, calendar);
        System.out.println(calendar);
        System.out.println(LocalTime.now());
        System.out.println(path);
//        Desktop.getDesktop().open(path.toFile());
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
                    .flatMap(e -> getDate((String) e.getKey(), (String) e.getValue()).stream())
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new RuntimeException("Failed reading Abfall dates", e);
        }
    }

    private static Set<LocalDate> getDate(String month, String listOfDays) {
        var days = Arrays.stream(listOfDays.split(",")).distinct().map(Integer::parseInt).collect(Collectors.toList());
        return days.stream().map(d -> LocalDate.of(2021, Integer.parseInt(month), d)).collect(Collectors.toSet());
    }

    static String generateCalendar(List<String> events) {
        var header = """
                BEGIN:VCALENDAR
                PRODID:-//xyz Corp//NONSGML PDA Calendar Version 1.0//EN
                VERSION:2.0
                """;
        var footer = "END:VCALENDAR";
        return String.format("%s%s%n%s", header, String.join(System.getProperty("line.separator"), events), footer);
    }

    static Event abfallEventForDate(LocalDate date, Abfall kind) {
        var dtstamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + 'T' + LocalTime.now()
                .format(DateTimeFormatter.ofPattern("HHmmss")) + 'Z';
        var dtstart = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        return new Event.EventBuilder()//
                .withUid(kind.name() + '-' + dtstart)
                .withDescription(SOURCE_PDF)
                .withDtstamp(dtstamp)
                .withDtstart(dtstart)
                .withDuration("P1D")
                .withSummary(kind.getLabel())
                .build();
    }
}

@Data
@Builder(setterPrefix = "with")
class Event {
    String dtstamp;
    String dtstart;
    String duration;
    String summary;
    String uid;
    String description;

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
