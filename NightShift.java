import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.sound.sampled.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;

/**
 * NIGHT SHIFT - a "survive the night" security guard game (original characters).
 *
 * Controls:
 *   A / D      toggle left / right door
 *   Q / E      toggle left / right hallway light (shows who is at the door)
 *   SPACE      raise / lower the camera monitor
 *   1-4        switch camera
 *   ENTER      start / restart
 *
 * Survive from 12 AM to 6 AM without running out of power or getting caught.
 * Compile:  javac NightShift.java
 * Run:      java NightShift
 */
public class NightShift extends JPanel implements ActionListener, KeyListener {

    static final int W = 960, H = 600;
    static final long HOUR_MS = 40_000;           // real time per in-game hour
    static final int LEFT = 0, RIGHT = 1;

    // Rooms: 0 stage, 1 hall, 2 left corridor, 3 right corridor, 4 left door, 5 right door
    static final int[] LEFT_PATH = {0, 1, 2, 4};
    static final int[] RIGHT_PATH = {0, 1, 3, 5};
    static final String[] CAM_NAMES = {"CAM 1 - STAGE", "CAM 2 - MAIN HALL", "CAM 3 - WEST CORRIDOR", "CAM 4 - EAST CORRIDOR"};

    enum State { MENU, NAME, PLAY, DEAD, WIN }

    static class Enemy {
        String name; Color color; int[] path; int idx = 0; int base; long timer; long interval; boolean roams;
        Enemy(String n, Color c, int[] p, int b, long iv, boolean r) {
            name = n; color = c; path = p; base = b; interval = iv; roams = r;
        }
        int room() { return path[idx]; }
        int side() { return path == LEFT_PATH ? LEFT : RIGHT; }
        boolean atDoor() { return idx == path.length - 1; }
    }

    final Random rnd = new Random();
    final Timer timer = new Timer(33, this);
    State state = State.MENU;
    Enemy[] enemies;
    boolean[] door = new boolean[2];
    boolean[] light = new boolean[2];
    boolean camUp = false;
    int cam = 0;
    double power;
    long elapsed, powerOutTime;
    String killer = "";
    String playerName = "";
    boolean ignoreTyped = false;
    long lastTick;

    public NightShift() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        addKeyListener(this);
        Snd.init();
        Db.async(Db::refresh);
        timer.start();
    }

    void startGame() {
        enemies = new Enemy[]{
            new Enemy("Barnaby Bear", new Color(140, 90, 40), LEFT_PATH, 4, 4200, false),
            new Enemy("Pepper Penguin", new Color(70, 90, 160), RIGHT_PATH, 3, 4600, false),
            new Enemy("Ziggy Raccoon", new Color(120, 120, 130), LEFT_PATH, 2, 5200, true)
        };
        door = new boolean[2];
        light = new boolean[2];
        camUp = false; cam = 0;
        power = 100; elapsed = 0; powerOutTime = 0;
        lastTick = System.currentTimeMillis();
        Snd.stopLoops();
        Db.status = "";
        state = State.PLAY;
    }

    int hour() { return (int) (elapsed / HOUR_MS); }

    // ---------- game logic ----------
    @Override public void actionPerformed(ActionEvent e) {
        long now = System.currentTimeMillis();
        long dt = Math.min(now - lastTick, 100);
        lastTick = now;
        if (state == State.PLAY) update(dt);
        repaint();
    }

    void update(long dt) {
        elapsed += dt;
        if (hour() >= 6) { state = State.WIN; Snd.stopLoops(); Snd.play(Snd.chime); recordScore(true); return; }

        // power drain
        if (power > 0) {
            int usage = 1 + (door[LEFT] ? 1 : 0) + (door[RIGHT] ? 1 : 0)
                    + (light[LEFT] ? 1 : 0) + (light[RIGHT] ? 1 : 0) + (camUp ? 1 : 0);
            power -= 0.17 * usage * dt / 1000.0;
            if (power <= 0) {
                power = 0;
                Snd.play(Snd.down);
                door[LEFT] = door[RIGHT] = light[LEFT] = light[RIGHT] = false;
                camUp = false;
            }
        } else {
            powerOutTime += dt;
            if (powerOutTime > 9000) { die("Barnaby Bear (lights out!)"); return; }
        }

        syncLoops();

        // enemy movement
        for (Enemy en : enemies) {
            en.timer += dt;
            if (en.timer >= en.interval) {
                en.timer = 0;
                int aggression = en.base + hour() * 2;
                if (rnd.nextInt(20) < aggression) advance(en);
                if (state != State.PLAY) return;
            }
        }
    }

    void advance(Enemy en) {
        if (!en.atDoor()) { en.idx++; Snd.play(en.atDoor() ? Snd.knock : Snd.step); return; }
        if (door[en.side()]) {                 // blocked: retreat
            en.idx = 1;
            if (en.roams) en.path = rnd.nextBoolean() ? LEFT_PATH : RIGHT_PATH;
        } else {
            die(en.name);
        }
    }

    void die(String who) {
        killer = who; state = State.DEAD;
        Snd.stopLoops();
        Snd.play(Snd.scream);
        recordScore(false);
    }

    void syncLoops() {
        boolean dark = power <= 0;
        Snd.loop(Snd.fan, !dark);
        Snd.loop(Snd.buzz, light[LEFT] || light[RIGHT]);
        Snd.loop(Snd.stat, camUp);
        Snd.loop(Snd.music, dark);
    }

    // ---------- input ----------
    @Override public void keyPressed(KeyEvent k) {
        int c = k.getKeyCode();
        if (state == State.NAME) {
            if (c == KeyEvent.VK_ENTER) { if (playerName.isBlank()) playerName = "Guest"; startGame(); }
            return;
        }
        if (c == KeyEvent.VK_ENTER && state == State.MENU) { state = State.NAME; return; }
        if (c == KeyEvent.VK_ENTER && state != State.PLAY) { startGame(); return; }
        if (c == KeyEvent.VK_N && state != State.PLAY) { ignoreTyped = true; state = State.NAME; return; }
        if (state != State.PLAY) return;
        boolean hasPower = power > 0;
        switch (c) {
            case KeyEvent.VK_A -> { if (hasPower) { door[LEFT] = !door[LEFT]; Snd.play(Snd.thump); } }
            case KeyEvent.VK_D -> { if (hasPower) { door[RIGHT] = !door[RIGHT]; Snd.play(Snd.thump); } }
            case KeyEvent.VK_Q -> { if (hasPower) { light[LEFT] = !light[LEFT]; Snd.play(Snd.blip); } }
            case KeyEvent.VK_E -> { if (hasPower) { light[RIGHT] = !light[RIGHT]; Snd.play(Snd.blip); } }
            case KeyEvent.VK_SPACE -> { if (hasPower) { camUp = !camUp; Snd.play(Snd.blip); } }
            case KeyEvent.VK_1 -> { cam = 0; Snd.play(Snd.blip); }
            case KeyEvent.VK_2 -> { cam = 1; Snd.play(Snd.blip); }
            case KeyEvent.VK_3 -> { cam = 2; Snd.play(Snd.blip); }
            case KeyEvent.VK_4 -> { cam = 3; Snd.play(Snd.blip); }
        }
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {
        if (ignoreTyped) { ignoreTyped = false; return; }
        if (state != State.NAME) return;
        char ch = e.getKeyChar();
        if (ch == '\b') {
            if (!playerName.isEmpty()) playerName = playerName.substring(0, playerName.length() - 1);
        } else if ((Character.isLetterOrDigit(ch) || ch == ' ') && playerName.length() < 12) {
            playerName += ch;
        }
    }

    // ---------- rendering ----------
    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, H);

        switch (state) {
            case MENU -> drawMenu(g);
            case NAME -> drawName(g);
            case PLAY -> { if (camUp) drawCamera(g); else drawOffice(g); drawHud(g); }
            case DEAD -> drawDead(g);
            case WIN -> drawWin(g);
        }
    }

    void centered(Graphics2D g, String s, int y) {
        int w = g.getFontMetrics().stringWidth(s);
        g.drawString(s, (W - w) / 2, y);
    }

    // ---------- scores / UI helpers ----------
    static String fmt(long sec) { return (sec / 60) + " min " + String.format("%02d", sec % 60) + " sec"; }
    static String hourLabel(int h) { return (h == 0 ? 12 : h) + " AM"; }

    void recordScore(boolean won) {
        final String nm = playerName.isBlank() ? "Guest" : playerName;
        final int secs = (int) (elapsed / 1000);
        final int hr = Math.min(hour(), 6);
        Db.status = "Saving score...";
        Db.async(() -> Db.save(nm, secs, hr, won));
    }

    void drawSummary(Graphics2D g, int y) {
        g.setColor(Color.WHITE);
        g.setFont(new Font("Monospaced", Font.BOLD, 20));
        centered(g, playerName + " survived " + fmt(elapsed / 1000) + " (reached " + hourLabel(Math.min(hour(), 6)) + ")", y);
        g.setFont(new Font("Monospaced", Font.PLAIN, 15));
        g.setColor(Color.LIGHT_GRAY);
        centered(g, Db.status, y + 24);
    }

    void drawLeaderboard(Graphics2D g) {
        java.util.List<String> t = Db.top;
        g.setFont(new Font("Monospaced", Font.BOLD, 15));
        g.setColor(new Color(255, 215, 80));
        centered(g, "-- TOP SURVIVORS --", 505);
        g.setFont(new Font("Monospaced", Font.PLAIN, 14));
        if (t.isEmpty()) {
            g.setColor(Color.GRAY);
            centered(g, Db.status.isEmpty() ? "No scores yet (start MySQL in XAMPP to save scores)" : Db.status, 530);
        } else {
            g.setColor(Color.WHITE);
            for (int i = 0; i < t.size(); i++) centered(g, t.get(i), 523 + i * 16);
        }
    }

    void drawName(Graphics2D g) {
        g.setColor(new Color(200, 30, 30));
        g.setFont(new Font("Monospaced", Font.BOLD, 48));
        centered(g, "NIGHT GUARD NAME", 200);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Monospaced", Font.BOLD, 40));
        String cursor = (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : " ";
        centered(g, playerName + cursor, 300);
        g.setColor(Color.GRAY);
        g.setFont(new Font("Monospaced", Font.PLAIN, 18));
        centered(g, "Type your name (max 12 characters), then press ENTER", 380);
    }

    void drawMenu(Graphics2D g) {
        g.setColor(new Color(200, 30, 30));
        g.setFont(new Font("Monospaced", Font.BOLD, 64));
        centered(g, "NIGHT SHIFT", 190);
        g.setColor(Color.LIGHT_GRAY);
        g.setFont(new Font("Monospaced", Font.PLAIN, 18));
        centered(g, "Survive the night shift at Grinny's Pizza Palace. 12 AM - 6 AM.", 250);
        centered(g, "A / D  - close left / right door      Q / E - hallway lights", 320);
        centered(g, "SPACE  - open cameras                 1-4   - change camera", 350);
        centered(g, "Doors, lights and cameras drain power. Don't run out!", 400);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Monospaced", Font.BOLD, 24));
        centered(g, "Press ENTER to start", 465);
        drawLeaderboard(g);
    }

    void drawOffice(Graphics2D g) {
        boolean dark = power <= 0;
        g.setColor(new Color(25, 25, 32));
        g.fillRect(0, 0, W, H);
        // back wall + desk
        g.setColor(new Color(40, 40, 50));
        g.fillRect(200, 80, 560, 330);
        g.setColor(new Color(70, 50, 35));
        g.fillRect(260, 380, 440, 140);
        g.setColor(new Color(20, 20, 20));
        g.fillRect(400, 395, 160, 90);                // monitor
        g.setColor(dark ? Color.DARK_GRAY : new Color(40, 140, 60));
        g.fillRect(408, 402, 144, 70);
        g.setColor(Color.GRAY);
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.drawString("[SPACE] CAMERAS", 420, 505);
        // fan
        double a = (elapsed / 60.0) % 360;
        g.setColor(dark ? new Color(40, 40, 40) : new Color(120, 120, 130));
        for (int i = 0; i < 3; i++) {
            double r = Math.toRadians(a + i * 120);
            g.drawLine(480, 330, (int) (480 + 40 * Math.cos(r)), (int) (330 + 40 * Math.sin(r)));
        }

        drawDoorway(g, LEFT, 30);
        drawDoorway(g, RIGHT, 780);

        if (dark) {
            g.setColor(new Color(0, 0, 0, 215));
            g.fillRect(0, 0, W, H);
            // glowing eyes creeping in
            if (powerOutTime > 4000 && (elapsed / 400) % 2 == 0) {
                g.setColor(Color.YELLOW);
                g.fillOval(445, 200, 14, 14);
                g.fillOval(500, 200, 14, 14);
            }
            g.setColor(new Color(200, 40, 40));
            g.setFont(new Font("Monospaced", Font.BOLD, 28));
            centered(g, "POWER OUT...", 110);
        }
    }

    void drawDoorway(Graphics2D g, int side, int x) {
        int y = 90, w = 150, h = 330;
        g.setColor(Color.BLACK);
        g.fillRect(x, y, w, h);
        if (light[side]) {
            g.setColor(new Color(235, 235, 170));
            g.fillRect(x + 6, y + 6, w - 12, h - 12);
            for (Enemy en : enemies) {
                if (en.atDoor() && en.side() == side) drawAnimatronic(g, en, x + w / 2, y + 230, 1.3, true);
            }
        }
        if (door[side]) {
            g.setColor(new Color(110, 115, 125));
            g.fillRect(x, y, w, h);
            g.setColor(new Color(80, 85, 95));
            for (int i = 0; i < 8; i++) g.fillRect(x, y + i * 42, w, 6);
        }
        g.setColor(new Color(90, 90, 100));
        g.drawRect(x, y, w, h);
        g.setColor(Color.GRAY);
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        String lbl = side == LEFT ? "[A] door  [Q] light" : "[D] door  [E] light";
        g.drawString(lbl, x + 5, y + h + 18);
    }

    void drawAnimatronic(Graphics2D g, Enemy en, int cx, int cy, double s, boolean eyesGlow) {
        g.setColor(en.color);
        g.fillOval((int) (cx - 30 * s), (int) (cy - 100 * s), (int) (60 * s), (int) (60 * s));   // head
        g.fillRoundRect((int) (cx - 35 * s), (int) (cy - 45 * s), (int) (70 * s), (int) (90 * s), 20, 20); // body
        g.fillOval((int) (cx - 30 * s), (int) (cy - 112 * s), (int) (20 * s), (int) (20 * s));   // ears
        g.fillOval((int) (cx + 10 * s), (int) (cy - 112 * s), (int) (20 * s), (int) (20 * s));
        g.setColor(eyesGlow ? Color.RED : Color.WHITE);
        g.fillOval((int) (cx - 18 * s), (int) (cy - 85 * s), (int) (11 * s), (int) (11 * s));
        g.fillOval((int) (cx + 7 * s), (int) (cy - 85 * s), (int) (11 * s), (int) (11 * s));
        g.setColor(Color.BLACK);
        g.fillRect((int) (cx - 14 * s), (int) (cy - 62 * s), (int) (28 * s), (int) (5 * s));      // mouth
    }

    void drawCamera(Graphics2D g) {
        g.setColor(new Color(15, 30, 20));
        g.fillRect(0, 0, W, H);
        // room drawing
        g.setColor(new Color(35, 60, 40));
        g.fillRect(100, 100, 760, 380);
        g.setColor(new Color(25, 45, 30));
        g.fillRect(100, 380, 760, 100);
        int count = 0;
        for (Enemy en : enemies) {
            if (en.room() == cam) {
                // flicker so they feel unsettling
                if (rnd.nextInt(12) != 0) drawAnimatronic(g, en, 300 + count * 190, 390, 1.5, false);
                count++;
            }
        }
        if (cam == 0) {          // stage decoration
            g.setColor(new Color(90, 60, 40));
            g.fillRect(150, 400, 660, 20);
            g.setColor(Color.GRAY);
            g.setFont(new Font("Monospaced", Font.BOLD, 18));
            g.drawString("~ Grinny's Pizza Palace ~", 360, 140);
        }
        // static noise
        g.setColor(new Color(200, 255, 200, 40));
        for (int i = 0; i < 400; i++) g.fillRect(rnd.nextInt(W), rnd.nextInt(H), 3, 1);
        for (int y = 0; y < H; y += 4) { g.setColor(new Color(0, 0, 0, 40)); g.drawLine(0, y, W, y); }
        g.setColor(new Color(220, 255, 220));
        g.setFont(new Font("Monospaced", Font.BOLD, 22));
        g.drawString(CAM_NAMES[cam], 120, 90);
        if ((elapsed / 500) % 2 == 0) { g.setColor(Color.RED); g.fillOval(830, 70, 14, 14); }
        g.setColor(new Color(220, 255, 220));
        g.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g.drawString("Press 1-4 to switch camera, SPACE to close", 120, 510);
    }

    void drawHud(Graphics2D g) {
        g.setFont(new Font("Monospaced", Font.BOLD, 26));
        g.setColor(Color.WHITE);
        int h = hour();
        g.drawString((h == 0 ? 12 : h) + " AM", W - 120, 40);
        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        g.drawString("Power: " + (int) Math.ceil(power) + "%", 20, H - 40);
        int usage = 1 + (door[LEFT] ? 1 : 0) + (door[RIGHT] ? 1 : 0)
                + (light[LEFT] ? 1 : 0) + (light[RIGHT] ? 1 : 0) + (camUp ? 1 : 0);
        g.drawString("Usage:", 20, H - 15);
        for (int i = 0; i < Math.min(usage, 6); i++) {
            g.setColor(i < 2 ? Color.GREEN : i < 4 ? Color.ORANGE : Color.RED);
            g.fillRect(100 + i * 22, H - 32, 18, 18);
        }
    }

    void drawDead(Graphics2D g) {
        Enemy shown = enemies[0];
        for (Enemy en : enemies) if (killer.startsWith(en.name)) shown = en;
        int shake = rnd.nextInt(30) - 15;
        g.setColor(new Color(60, 0, 0));
        g.fillRect(0, 0, W, H);
        drawAnimatronic(g, shown, W / 2 + shake, 480 + shake / 2, 5.0, true);
        g.setColor(Color.RED);
        g.setFont(new Font("Monospaced", Font.BOLD, 48));
        centered(g, "CAUGHT BY " + killer.toUpperCase(), 70);
        g.setColor(Color.WHITE);
        drawSummary(g, H - 75);
        g.setFont(new Font("Monospaced", Font.BOLD, 20));
        g.setColor(Color.WHITE);
        centered(g, "Press ENTER to try again   |   N to change name", H - 18);
    }

    void drawWin(Graphics2D g) {
        g.setColor(new Color(255, 215, 80));
        g.setFont(new Font("Monospaced", Font.BOLD, 90));
        centered(g, "6 AM", 270);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Monospaced", Font.BOLD, 28));
        centered(g, "You survived the night!", 340);
        g.setFont(new Font("Monospaced", Font.PLAIN, 20));
        centered(g, "Press ENTER to play again   |   N to change name", 500);
        drawSummary(g, 400);
    }

    // ---------- database (XAMPP MySQL / MariaDB via JDBC) ----------
    static class Db {
        // Defaults for XAMPP: user "root", empty password. The database is created automatically.
        static final String URL = "jdbc:mysql://localhost:3306/nightshift?createDatabaseIfNotExist=true";
        static final String USER = "root", PASS = "";
        static volatile String status = "";
        static volatile java.util.List<String> top = new java.util.ArrayList<>();

        static Connection open() throws SQLException {
            Connection c = DriverManager.getConnection(URL, USER, PASS);
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS scores ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "player_name VARCHAR(50) NOT NULL,"
                    + "survived_seconds INT NOT NULL,"
                    + "hour_reached TINYINT NOT NULL,"
                    + "won BOOLEAN NOT NULL DEFAULT 0,"
                    + "played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            }
            return c;
        }

        static void save(String name, int seconds, int hour, boolean won) {
            try (Connection c = open();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO scores (player_name, survived_seconds, hour_reached, won) VALUES (?,?,?,?)")) {
                ps.setString(1, name);
                ps.setInt(2, seconds);
                ps.setInt(3, hour);
                ps.setBoolean(4, won);
                ps.executeUpdate();
                status = "Score saved to database!";
            } catch (SQLException ex) {
                status = "Database offline - score not saved";
            }
            refresh();
        }

        static void refresh() {
            try (Connection c = open();
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT player_name, survived_seconds, hour_reached, won "
                     + "FROM scores ORDER BY survived_seconds DESC LIMIT 5")) {
                java.util.List<String> list = new java.util.ArrayList<>();
                int rank = 1;
                while (rs.next()) {
                    list.add(rank++ + ". " + rs.getString(1) + " - " + fmt(rs.getInt(2))
                        + " (" + hourLabel(rs.getInt(3)) + ")" + (rs.getBoolean(4) ? " WIN" : ""));
                }
                top = list;
            } catch (SQLException ex) {
                top = new java.util.ArrayList<>();
                if (status.isEmpty()) status = "Database offline - leaderboard unavailable";
            }
        }

        static void async(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.start();
        }
    }

    // ---------- procedurally generated sound effects (no audio files needed) ----------
    static class Snd {
        static final float SR = 22050f;
        static final double TAU = Math.PI * 2;
        static final Random R = new Random();
        static Clip fan, buzz, stat, thump, blip, step, knock, scream, chime, down, music;

        static double noise() { return R.nextDouble() * 2 - 1; }

        static byte[] gen(double sec, java.util.function.DoubleUnaryOperator f) {
            int n = (int) (sec * SR);
            byte[] b = new byte[n * 2];
            for (int i = 0; i < n; i++) {
                double v = Math.max(-1, Math.min(1, f.applyAsDouble(i / (double) SR)));
                short sv = (short) (v * 32767);
                b[2 * i] = (byte) sv;
                b[2 * i + 1] = (byte) (sv >> 8);
            }
            return b;
        }

        static Clip clip(byte[] d) {
            try {
                AudioFormat fmt = new AudioFormat(SR, 16, 1, true, false);
                Clip c = AudioSystem.getClip();
                c.open(fmt, d, 0, d.length);
                return c;
            } catch (Exception e) {
                return null;   // no audio device: game still runs silently
            }
        }

        static double bell(double t, double freq, double decay) {
            return Math.sin(TAU * freq * t) * Math.exp(-t * decay) * 0.4
                 + Math.sin(TAU * freq * 2 * t) * Math.exp(-t * decay * 1.5) * 0.15;
        }

        // A slow, minor-key music-box lullaby (original melody): warbling pitch,
        // a slightly out-of-tune second voice, a low drone and echo.
        static byte[] makeLullaby() {
            final double beat = 0.55;
            final double[][] notes = {
                {440, 1}, {523.25, 1}, {659.25, 1},
                {587.33, 1.5}, {523.25, 0.5}, {493.88, 1},
                {440, 1}, {523.25, 1}, {440, 1},
                {415.3, 1}, {493.88, 1}, {329.63, 1},
                {440, 1}, {523.25, 1}, {659.25, 1},
                {698.46, 1}, {659.25, 1}, {587.33, 1},
                {523.25, 1}, {493.88, 1}, {440, 1},
                {415.3, 2}, {440, 1}
            };
            double[] start = new double[notes.length];
            double cur = 0;
            for (int i = 0; i < notes.length; i++) { start[i] = cur; cur += notes[i][1] * beat; }
            int n = (int) ((cur + 2.0) * SR);
            double[] dry = new double[n];
            for (int i = 0; i < n; i++) {
                double t = i / (double) SR, v = 0;
                for (int k = 0; k < notes.length; k++) {
                    double tt = t - start[k];
                    if (tt < 0 || tt > 2.5) continue;
                    double f = notes[k][0] * (1 + 0.004 * Math.sin(TAU * 0.5 * t));   // wobbling pitch
                    v += bell(tt, f, 3.2) + 0.45 * bell(tt, f * 1.011, 3.6);           // detuned twin
                }
                v += Math.sin(TAU * 110 * t) * 0.05 * (0.6 + 0.4 * Math.sin(TAU * 0.2 * t)); // drone
                dry[i] = v * 0.6;
            }
            int d1 = (int) (0.28 * SR), d2 = (int) (0.57 * SR);
            byte[] b = new byte[n * 2];
            for (int i = 0; i < n; i++) {
                double v = dry[i];
                if (i >= d1) v += dry[i - d1] * 0.35;
                if (i >= d2) v += dry[i - d2] * 0.2;
                short sv = (short) (Math.tanh(v) * 32767);
                b[2 * i] = (byte) sv;
                b[2 * i + 1] = (byte) (sv >> 8);
            }
            return b;
        }

        static void init() {
            // ambient loops (whole number of cycles per second so they loop cleanly)
            fan = clip(gen(1, t -> Math.sin(TAU * 60 * t) * 0.12 + Math.sin(TAU * 120 * t) * 0.06 + noise() * 0.03));
            buzz = clip(gen(1, t -> Math.sin(TAU * 120 * t) * 0.18 + Math.sin(TAU * 360 * t) * 0.07 + noise() * 0.03));
            stat = clip(gen(1, t -> noise() * 0.10));

            // door slam
            thump = clip(gen(0.45, t -> Math.sin(TAU * (55 + 40 * Math.exp(-t * 20)) * t) * Math.exp(-t * 10) * 0.9
                    + noise() * Math.exp(-t * 40) * 0.4 + Math.sin(TAU * 320 * t) * Math.exp(-t * 15) * 0.12));
            // camera / light click
            blip = clip(gen(0.08, t -> Math.sin(TAU * 1200 * t) * Math.exp(-t * 30) * 0.3));
            // distant footstep
            step = clip(gen(0.2, t -> Math.sin(TAU * 90 * t) * Math.exp(-t * 25) * 0.5 + noise() * Math.exp(-t * 60) * 0.15));
            // double knock at the door
            knock = clip(gen(0.5, t -> {
                double a = 0;
                for (double st : new double[]{0, 0.2}) {
                    double tt = t - st;
                    if (tt >= 0) a += Math.sin(TAU * 140 * tt) * Math.exp(-tt * 30) * 0.9 + noise() * Math.exp(-tt * 80) * 0.3;
                }
                return a;
            }));
            // jumpscare: distorted sweep + noise
            scream = clip(gen(1.3, t -> {
                double env = t < 1.0 ? 1 : Math.max(0, 1 - (t - 1.0) / 0.3);
                double f = 300 + 500 * t;
                double saw = ((f * t) % 1.0) * 2 - 1;
                double wob = Math.sin(TAU * (700 + 300 * Math.sin(TAU * 18 * t)) * t);
                return Math.tanh((saw * 0.8 + noise() * 0.7 + wob * 0.5) * 1.8) * env * 0.9;
            }));
            // power down whine
            down = clip(gen(1.5, t -> Math.sin(TAU * 200 * (1 - Math.exp(-2 * t))) * Math.exp(-t * 1.2) * 0.6
                    + noise() * Math.exp(-t * 6) * 0.2));
            // creepy lullaby that starts the instant the power dies
            music = clip(makeLullaby());
            // 6 AM chime
            final double[] ch = {523.25, 659.25, 783.99, 1046.5};
            chime = clip(gen(2.5, t -> {
                double a = 0;
                for (int k = 0; k < ch.length; k++) if (t >= k * 0.3) a += bell(t - k * 0.3, ch[k], 3);
                return a * 0.8;
            }));
        }

        static void play(Clip c) {
            if (c == null) return;
            c.stop();
            c.setFramePosition(0);
            c.start();
        }

        static void loop(Clip c, boolean on) {
            if (c == null) return;
            if (on && !c.isRunning()) { c.setFramePosition(0); c.loop(Clip.LOOP_CONTINUOUSLY); }
            else if (!on && c.isRunning()) c.stop();
        }

        static void stopLoops() {
            loop(fan, false); loop(buzz, false); loop(stat, false); loop(music, false);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Night Shift");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setResizable(false);
            f.add(new NightShift());
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}