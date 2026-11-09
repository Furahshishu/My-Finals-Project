BEAT BRAWL + XAMPP / phpMyAdmin SETUP

1. Start Apache and MySQL in the XAMPP Control Panel.

2. Create the database:
   - Open http://localhost/phpmyadmin
   - Click the "Import" tab, choose setup.sql, click Import.
     (Or click the "SQL" tab, paste the contents of setup.sql, click Go.)
   This creates the database "beat_brawl" with a "scores" table.

3. Copy this whole folder into XAMPP's htdocs folder, for example:
   C:\xampp\htdocs\beatbrawl\

4. Open the game at:
   http://localhost/beatbrawl/
   (Do NOT double-click index.html. It must be opened through localhost
   or scores cannot be saved.)

5. Type your name under the game, then play. When a song ends (win or lose)
   your name, score, accuracy, best combo and result are saved.
   Use "Leaderboard" in the main menu to see the top 10.

View the raw data any time in phpMyAdmin: beat_brawl > scores.

If your MySQL root user has a password, edit db.php and set DB_PASS.
