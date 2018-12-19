package bitworks.tz;

import org.ini4j.Ini;
import org.ini4j.IniPreferences;
import org.pcap4j.core.PcapNativeException;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;

public class App
{
    private static DatabaseClient databaseClient = null;
    private static Ini configFile = null;
    private static UrlChecker urlChecker = null;

    public static void main( String[] args ) throws SQLException, IOException,
            ParseException, PcapNativeException, InterruptedException {
        /**
         * Получение настроек из файла конфигурации
         * Испульзуется файл типа .ini
         */
        configFile = new Ini(new File("config.ini"));
        java.util.prefs.Preferences prefs = new IniPreferences(configFile);
        String databaseHost = prefs.node("database").get("host", "localhost");
        int databaseHostPort = Integer.parseInt(prefs.node("database").get("port", "5432"));
        String databaseName = prefs.node("database").get("name", "postgresql");
        String databaseUsername = prefs.node("database").get("username", "user");
        String databasePassword = prefs.node("database").get("password", "password");
        String tableName = prefs.node("database").get("tablename", "account");
        String date = prefs.node("input").get("date", "0000-00-00");


        /**
         * Создание объекта базы данных с полученными из конфига параметрами
         * а также объекта проверяющего статусы соединений
         */
        databaseClient = new DatabaseClient(databaseHost, databaseHostPort,
                databaseName, databaseUsername, databasePassword);
        urlChecker = new UrlChecker(databaseClient);

        /**
         * Запуск проверки статусов с полученной из конфига датой
         */
        urlChecker.startCheck(tableName, new SimpleDateFormat("yyyy-MM-dd").parse(date));

        System.exit(0);
    }
}
