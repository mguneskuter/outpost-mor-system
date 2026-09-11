import java.io.*;
import java.math.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

class GenerateFxSeed {
  private static final List<String> CURRENCIES = List.of("CZK", "DKK", "EUR", "GBP", "HUF", "PLN", "RON", "SEK", "USD");
  private static final Map<String, Long> IDS = Map.of("CZK", 1L, "DKK", 2L, "EUR", 3L, "GBP", 4L, "HUF", 5L, "PLN", 6L, "RON", 7L, "SEK", 8L, "USD", 9L);
  private static final int SCALE = 10;

  public static void main(String[] args) throws Exception {
    Path root = Path.of("local");
    Path fixture = root.resolve("fixtures/fx/fx.csv");
    Path output = root.resolve("generated/fx");
    Files.createDirectories(output);
    Map<LocalDate, Map<String, BigDecimal>> observations = read(fixture);
    List<LocalDate> dates = new ArrayList<>(observations.keySet());
    dates.sort(Comparator.naturalOrder());
    LocalDate first = dates.getFirst();
    Map<LocalDate, Map<String, BigDecimal>> rates = new LinkedHashMap<>();
    for (int i = 0; i < 12; i++) {
      LocalDate date = first.plusDays(i);
      LocalDate source = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY
          ? first.plusDays(4) : dates.stream().filter(candidate -> candidate.getDayOfWeek() == date.getDayOfWeek()).findFirst().orElseThrow();
      rates.put(date, observations.get(source));
    }
    writeRates(output.resolve("fx_rate.sql"), rates);
    writeFees(output.resolve("fx_fee.sql"));
  }

  private static Map<LocalDate, Map<String, BigDecimal>> read(Path path) throws IOException {
    Map<LocalDate, Map<String, BigDecimal>> result = new TreeMap<>();
    List<String> lines = Files.readAllLines(path);
    for (String line : lines.subList(1, lines.size())) {
      String[] fields = line.split(",");
      if (fields.length != 8) throw new IllegalArgumentException("invalid ECB CSV row: " + line);
      result.computeIfAbsent(LocalDate.parse(fields[6]), ignored -> new HashMap<>()).put(fields[2], new BigDecimal(fields[7]));
    }
    if (result.size() != 5) throw new IllegalArgumentException("fixture must contain five observations");
    return result;
  }

  private static void writeRates(Path path, Map<LocalDate, Map<String, BigDecimal>> rates) throws IOException {
    try (BufferedWriter out = Files.newBufferedWriter(path)) {
      out.write("-- Weekday observations are replayed; Saturday and Sunday replay Friday.\nBEGIN;\n");
      long id = 1;
      for (var date : rates.entrySet()) for (String base : CURRENCIES) for (String quote : CURRENCIES) if (!base.equals(quote)) {
        BigDecimal basePerEur = base.equals("EUR") ? BigDecimal.ONE : date.getValue().get(base);
        BigDecimal quotePerEur = quote.equals("EUR") ? BigDecimal.ONE : date.getValue().get(quote);
        BigDecimal rate = quotePerEur.divide(basePerEur, SCALE, RoundingMode.HALF_EVEN);
        insert(out, "fx_rate", id++, IDS.get(base), IDS.get(quote), date.getKey().toString(), rate.setScale(SCALE), "'ECB'");
      }
      out.write("SELECT setval('fx_rate_seq', COALESCE((SELECT max(fx_rate_id) FROM fx_rate), 1), true);\nCOMMIT;\n");
    }
  }

  private static void writeFees(Path path) throws IOException {
    try (BufferedWriter out = Files.newBufferedWriter(path)) {
      out.write("BEGIN;\n");
      long id = 1;
      for (String base : CURRENCIES) for (String quote : CURRENCIES) if (!base.equals(quote))
        insert(out, "fx_fee", id++, IDS.get(base), IDS.get(quote), null, BigDecimal.valueOf(100), null);
      out.write("SELECT setval('fx_fee_seq', COALESCE((SELECT max(fx_fee_id) FROM fx_fee), 1), true);\nCOMMIT;\n");
    }
  }

  private static void insert(Writer out, String table, long id, long base, long quote, String date, BigDecimal value, String source) throws IOException {
    String key = table.equals("fx_rate") ? "base_currency_id, quote_currency_id, rate_date" : "base_currency_id, quote_currency_id";
    String columns = table.equals("fx_rate") ? "fx_rate_id, base_currency_id, quote_currency_id, rate_date, rate, source" : "fx_fee_id, base_currency_id, quote_currency_id, fee_rate_bps";
    String values = table.equals("fx_rate") ? id + ", " + base + ", " + quote + ", DATE '" + date + "', " + value.toPlainString() + ", " + source : id + ", " + base + ", " + quote + ", " + value.toPlainString();
    String lookup = table.equals("fx_rate")
        ? "base_currency_id = " + base + " AND quote_currency_id = " + quote + " AND rate_date = DATE '" + date + "'"
        : "base_currency_id = " + base + " AND quote_currency_id = " + quote;
    String equal = table.equals("fx_rate")
        ? "rate = " + value.toPlainString() + " AND source = " + source
        : "fee_rate_bps = " + value.toPlainString();
    out.write("DO $$ BEGIN IF EXISTS (SELECT 1 FROM " + table + " WHERE " + lookup + " AND NOT (" + equal + ")) THEN RAISE EXCEPTION 'divergent " + table + " row'; END IF; END $$;\n");
    out.write("INSERT INTO " + table + " (" + columns + ") VALUES (" + values + ") ON CONFLICT (" + key + ") DO NOTHING;\n");
  }
}
