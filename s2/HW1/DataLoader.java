package lab;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

import java.io.StringReader;
import java.sql.*;
import java.sql.Date;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class DataLoader {

    static final int USERS = 100_000;
    static final int DIRECTORS = 5_000;
    static final int ACTORS = 20_000;
    static final int MOVIES = 50_000;

    static final int FACT = 250_000;

    static final double SKEW_TOP_SHARE = 0.10;
    static final double SKEW_EVENT_SHARE = 0.70;

    static final String[] DEVICES = {"TV", "MOBILE", "TABLET", "WEB", "CONSOLE"}; // low-cardinality 5
    static final String[] TAG_POOL = {"funny", "dark", "classic", "new", "award", "family", "action", "slow", "twist", "space"};
    static final String[] WORDS = ("great amazing boring slow fast emotional plot acting direction music visuals "
            + "masterpiece awful okay decent surprising predictable").split(" ");

    public static void main(String[] args) throws Exception {
        String url = env("JDBC_URL", "jdbc:postgresql://localhost:5444/CinemaMigr");
        String user = env("JDBC_USER", "postgres");
        String pass = env("JDBC_PASS", "postgres");

        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            c.setAutoCommit(false);

            Map<String, long[]> ref = loadRefRanges(c);
            System.out.println("Ref ranges loaded.");

            insertUsers(c, ref, USERS);
            insertDirectors(c, ref, DIRECTORS);
            insertActors(c, ref, ACTORS);
            insertMovies(c, ref, DIRECTORS, MOVIES);

            c.commit();
            System.out.println("Dimensions inserted.");

            copyViewing(c, USERS, MOVIES, FACT);
            copyRental(c, ref, USERS, MOVIES, FACT);
            copyPurchase(c, ref, USERS, MOVIES, FACT);
            copyReview(c, USERS, MOVIES, FACT);

            c.commit();
            System.out.println("Facts inserted.");
        }
    }

    static Map<String, long[]> loadRefRanges(Connection c) throws SQLException {
        Map<String, long[]> m = new HashMap<>();
        m.put("role_id", minMax(c, "select min(role_id), max(role_id) from user_role"));
        m.put("country_id", minMax(c, "select min(country_id), max(country_id) from country"));
        m.put("language_id", minMax(c, "select min(language_id), max(language_id) from language"));
        m.put("age_rating_id", minMax(c, "select min(age_rating_id), max(age_rating_id) from age_rating"));
        m.put("rental_status_id", minMax(c, "select min(rental_status_id), max(rental_status_id) from rental_status"));
        m.put("payment_method_id", minMax(c, "select min(method_id), max(method_id) from payment_method"));
        return m;
    }

    static long[] minMax(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return new long[]{rs.getLong(1), rs.getLong(2)};
        }
    }

    static void insertUsers(Connection c, Map<String, long[]> ref, int n) throws SQLException {
        String sql = "insert into users(name, role_id, email, registration_date) values (?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 1; i <= n; i++) {
                ps.setString(1, "User " + i);

                ps.setLong(2, randBetween(ref.get("role_id")));

                ps.setString(3, "user" + i + "@mail.test");

                ps.setTimestamp(4, Timestamp.from(randomInstantYearsBack(5)));

                ps.addBatch();
                if (i % 5000 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
    }

    static void insertDirectors(Connection c, Map<String, long[]> ref, int n) throws SQLException {
        String sql = "insert into director(name, birth_date, country_id, biography) values (?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 1; i <= n; i++) {
                ps.setString(1, "Director " + i);
                ps.setDate(2, Date.valueOf(LocalDate.now().minusYears(25 + rnd(50)).minusDays(rnd(365))));
                ps.setLong(3, randBetween(ref.get("country_id")));

                if (chance(0.10)) ps.setNull(4, Types.VARCHAR);
                else ps.setString(4, sentence(20 + rnd(40)));

                ps.addBatch();
                if (i % 5000 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
    }

    static void insertActors(Connection c, Map<String, long[]> ref, int n) throws SQLException {
        String sql = "insert into actor(name, birth_date, country_id, biography) values (?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 1; i <= n; i++) {
                ps.setString(1, "Actor " + i);
                ps.setDate(2, Date.valueOf(LocalDate.now().minusYears(18 + rnd(60)).minusDays(rnd(365))));
                ps.setLong(3, randBetween(ref.get("country_id")));
                if (chance(0.15)) ps.setNull(4, Types.VARCHAR); // NULL 15%
                else ps.setString(4, sentence(15 + rnd(50)));

                ps.addBatch();
                if (i % 5000 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
    }

    static void insertMovies(Connection c, Map<String, long[]> ref, int directors, int n) throws SQLException {
        String sql = "insert into movie(title, description, release_year, duration, age_rating_id, language_id, country_id, director_id) " +
                "values (?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 1; i <= n; i++) {
                ps.setString(1, "Movie " + i);

                if (chance(0.10)) ps.setNull(2, Types.VARCHAR);
                else ps.setString(2, sentence(40 + rnd(80)));

                ps.setInt(3, 1980 + rnd(45)); // диапазон
                ps.setInt(4, 60 + rnd(120));

                ps.setLong(5, randBetween(ref.get("age_rating_id")));
                ps.setLong(6, randBetween(ref.get("language_id")));
                ps.setLong(7, randBetween(ref.get("country_id")));
                ps.setLong(8, 1 + rnd(directors));

                ps.addBatch();
                if (i % 5000 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
    }

    static void copyViewing(Connection c, int users, int movies, int n) throws Exception {
        CopyManager cm = c.unwrap(PGConnection.class).getCopyAPI();

        StringBuilder sb = new StringBuilder(n * 100);

        for (int i = 0; i < n; i++) {
            long userId = skewUser(users);
            long movieId = 1 + rnd(movies);

            Instant ts = randomInstantDaysBack(365);
            int progress = rnd(101);

            String device = chance(0.10) ? null : DEVICES[rnd(DEVICES.length)];

            String tags = chance(0.15) ? null : pgTextArray(sampleTags());

            String meta = chance(0.15) ? null : json("{\"quality\":\"" + pick("sd","hd","4k") + "\",\"subtitles\":" + chance(0.5) + "}");

            String geo = chance(0.15) ? null : "(" + (4 + rndDouble()*10) + "," + (50 + rndDouble()*10) + ")";

            sb.append(userId).append('\t')
                    .append(movieId).append('\t')
                    .append(Timestamp.from(ts)).append('\t')
                    .append(progress).append('\t')
                    .append(nullToCopy(device)).append('\t')
                    .append(nullToCopy(tags)).append('\t')
                    .append(nullToCopy(meta)).append('\t')
                    .append(nullToCopy(geo)).append('\n');
        }

        String copySql = "COPY viewing(user_id, movie_id, viewing_date, progress, device, tags, meta, geo) FROM STDIN";
        cm.copyIn(copySql, new StringReader(sb.toString()));
        System.out.println("viewing copied: " + n);
    }

    static void copyRental(Connection c, Map<String, long[]> ref, int users, int movies, int n) throws Exception {
        CopyManager cm = c.unwrap(PGConnection.class).getCopyAPI();
        StringBuilder sb = new StringBuilder(n * 110);

        for (int i = 0; i < n; i++) {
            long userId = skewUser(users);
            long movieId = 1 + rnd(movies);
            Instant rentalDate = randomInstantDaysBack(365);

            Instant returnDate = chance(0.20) ? null : rentalDate.plus(Duration.ofDays(1 + rnd(20)));

            double price = 1.99 + rndDouble() * 8.0;

            long statusId = randBetween(ref.get("rental_status_id"));

            String period = chance(0.10) ? null : rangeTs(rentalDate, (returnDate != null ? returnDate : rentalDate.plus(Duration.ofDays(3))));

            String notes = chance(0.15) ? null : sentence(10 + rnd(30));

            sb.append(userId).append('\t')
                    .append(movieId).append('\t')
                    .append(Timestamp.from(rentalDate)).append('\t')
                    .append(nullToCopy(returnDate == null ? null : Timestamp.from(returnDate).toString())).append('\t')
                    .append(String.format(Locale.US, "%.2f", price)).append('\t')
                    .append(statusId).append('\t')
                    .append(nullToCopy(period)).append('\t')
                    .append(nullToCopy(notes)).append('\n');
        }

        String copySql = "COPY rental(user_id, movie_id, rental_date, return_date, price, rental_status_id, rental_period, notes) FROM STDIN";
        cm.copyIn(copySql, new StringReader(sb.toString()));
        System.out.println("rental copied: " + n);
    }

    static void copyPurchase(Connection c, Map<String, long[]> ref, int users, int movies, int n) throws Exception {
        CopyManager cm = c.unwrap(PGConnection.class).getCopyAPI();
        StringBuilder sb = new StringBuilder(n * 120);

        for (int i = 0; i < n; i++) {
            long userId = skewUser(users);
            long movieId = 1 + rnd(movies);
            Instant ts = randomInstantDaysBack(365);

            double price = 2.99 + rndDouble() * 25.0;
            long methodId = randBetween(ref.get("payment_method_id"));

            String coupon = chance(0.10) ? null : ("C" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));

            String payMeta = chance(0.15) ? null : json("{\"provider\":\"" + pick("visa","mc","paypal") +
                    "\",\"country\":\"" + pick("USA","UK","NL","DE") + "\"}");

            int base = (int)Math.round(price * 100);
            String amountRange = chance(0.10) ? null : ("[" + base + "," + (base + rnd(500)) + "]");

            sb.append(userId).append('\t')
                    .append(movieId).append('\t')
                    .append(Timestamp.from(ts)).append('\t')
                    .append(String.format(Locale.US, "%.2f", price)).append('\t')
                    .append(methodId).append('\t')
                    .append(nullToCopy(coupon)).append('\t')
                    .append(nullToCopy(payMeta)).append('\t')
                    .append(nullToCopy(amountRange)).append('\n');
        }

        String copySql = "COPY purchase(user_id, movie_id, purchase_date, price, method_id, coupon_code, payment_meta, amount_range) FROM STDIN";
        cm.copyIn(copySql, new StringReader(sb.toString()));
        System.out.println("purchase copied: " + n);
    }

    static void copyReview(Connection c, int users, int movies, int n) throws Exception {
        CopyManager cm = c.unwrap(PGConnection.class).getCopyAPI();
        StringBuilder sb = new StringBuilder(n * 120);

        HashSet<Long> used = new HashSet<>(n * 2);

        int inserted = 0;
        while (inserted < n) {
            long userId = skewUser(users);
            long movieId = 1 + rnd(movies);
            long key = (userId << 20) ^ movieId;
            if (!used.add(key)) continue;

            int rating = 1 + rnd(10);

            String comment = chance(0.15) ? null : sentence(8 + rnd(25));

            String sentiment = chance(0.05) ? null : String.valueOf(1 + rnd(3));

            Instant ts = randomInstantDaysBack(365);

            sb.append(userId).append('\t')
                    .append(movieId).append('\t')
                    .append(rating).append('\t')
                    .append(nullToCopy(comment)).append('\t')
                    .append(Timestamp.from(ts)).append('\t')
                    .append(nullToCopy(sentiment)).append('\n');

            inserted++;
            if (inserted % 50_000 == 0) System.out.println("review prepared: " + inserted);
        }

        String copySql = "COPY review(user_id, movie_id, rating, comment, review_date, sentiment) FROM STDIN";
        cm.copyIn(copySql, new StringReader(sb.toString()));
        System.out.println("review copied: " + n);
    }

    static long skewUser(int users) {
        int top = (int) Math.max(1, Math.round(users * SKEW_TOP_SHARE));
        if (chance(SKEW_EVENT_SHARE)) {
            int k = 1 + (int) Math.floor(Math.pow(rndDouble(), 2) * top);
            return k;
        } else {
            return 1 + top + rnd(users - top);
        }
    }

    static boolean chance(double p) { return rndDouble() < p; }
    static int rnd(int bound) { return ThreadLocalRandom.current().nextInt(bound); }
    static double rndDouble() { return ThreadLocalRandom.current().nextDouble(); }

    static long randBetween(long[] minMax) {
        long min = minMax[0], max = minMax[1];
        return min + (long) Math.floor(rndDouble() * (max - min + 1));
    }

    static Instant randomInstantYearsBack(int years) {
        long days = (long) years * 365;
        return randomInstantDaysBack((int) days);
    }

    static Instant randomInstantDaysBack(int daysBack) {
        long now = Instant.now().toEpochMilli();
        long past = Instant.now().minus(Duration.ofDays(daysBack)).toEpochMilli();
        long t = past + (long) Math.floor(rndDouble() * (now - past));
        return Instant.ofEpochMilli(t);
    }

    static String sentence(int words) {
        StringBuilder sb = new StringBuilder(words * 6);
        for (int i = 0; i < words; i++) {
            if (i > 0) sb.append(' ');
            sb.append(WORDS[rnd(WORDS.length)]);
        }
        return sb.toString();
    }

    static String pick(String... xs) { return xs[rnd(xs.length)]; }

    static String[] sampleTags() {
        int k = rnd(6);
        if (k == 0) return new String[0];
        String[] out = new String[k];
        for (int i = 0; i < k; i++) out[i] = TAG_POOL[rnd(TAG_POOL.length)];
        return out;
    }

    static String pgTextArray(String[] arr) {
        if (arr.length == 0) return "{}";
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(arr[i]);
        }
        sb.append('}');
        return sb.toString();
    }

    static String json(String s) {
        return s;
    }

    static String rangeTs(Instant a, Instant b) {
        return "[" + Timestamp.from(a) + "," + Timestamp.from(b) + ")";
    }

    static String nullToCopy(String s) {
        return (s == null) ? "\\N" : s;
    }

    static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
