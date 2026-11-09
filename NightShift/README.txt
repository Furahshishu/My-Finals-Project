NIGHT SHIFT (Java) + XAMPP / phpMyAdmin SETUP

The game saves each run (name, time survived, hour reached, win or loss) to MySQL
on its own. It creates the "nightshift" database and the "scores" table the
first time it runs, so there is no SQL file to import.

WHAT YOU NEED
1. XAMPP with MySQL started (XAMPP Control Panel > MySQL > Start).
2. A JDK (Java 17 or newer). Check with:  javac -version
3. The MySQL JDBC driver ("MySQL Connector/J"), a file named like
   mysql-connector-j-8.x.x.jar. Download it from https://dev.mysql.com/downloads/connector/j/
   (choose "Platform Independent", then take the .jar from inside the zip).
   Put the .jar inside the "lib" folder next to NightShift.java.

RUN IT (Windows)
   Double-click run.bat

RUN IT (manual)
   javac -cp "lib/*" -d out NightShift.java
   java  -cp "out;lib/*" NightShift          (on Mac/Linux use ":" instead of ";")

PLAY
   Press ENTER, type your name, press ENTER again.
   A / D doors, Q / E lights, SPACE cameras, 1-4 switch camera.
   When you win or get caught, the score is saved. The main menu shows the top 5.

SEE THE DATA IN phpMyAdmin
   http://localhost/phpmyadmin > nightshift > scores > Browse

TROUBLESHOOTING
   "Database offline - score not saved": MySQL is not running in XAMPP, the .jar
   is missing from lib, or your root user has a password. If it has one, set it
   in NightShift.java (the PASS line in the Db class).
   If the MySQL connector will not connect to XAMPP's MariaDB, use the MariaDB
   Connector/J .jar instead and change the URL in NightShift.java to start with
   jdbc:mariadb://
