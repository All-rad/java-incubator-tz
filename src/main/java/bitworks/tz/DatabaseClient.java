package bitworks.tz;

import java.sql.*;

import org.postgresql.ds.PGPoolingDataSource;

/**
 * @brief Класс для взаимодействия с базой данных
 */
public class DatabaseClient {
    private PGPoolingDataSource connectionPool;

    public DatabaseClient(
            String host,
            int port,
            String dbName,
            String username,
            String password
    ) throws SQLException {

        /**
         * Подключение к базе без пула коннектов для того чтобы узнать
         * максимальное количество коннектов к данной базе за вычетом текущих
         * соединений который возможно используются другими приложениями
         */
        String url = "jdbc:postgresql://"
                + host + ":" + port
                + "/" + dbName;

        Connection conn = java.sql.DriverManager.getConnection(url, username, password);
        Statement statement = conn.createStatement();
        /**
         * Получение максимального числа коннектов
         */
        ResultSet resultSet = statement.executeQuery("SHOW max_connections");
        resultSet.next();
        int maxConnections = resultSet.getInt(1);
        /**
         * Получение количества текущих соединений
         */
        resultSet = statement.executeQuery("SELECT COUNT(*) FROM pg_stat_activity");
        resultSet.next();
        int currentConnections = resultSet.getInt(1);

        /**
         * Закрытие соединения с базой и создание пула коннектов
         */
        resultSet.close();
        statement.close();
        conn.close();
        connectionPool = new PGPoolingDataSource();

        /**
         * Установка настроек для пула коннектов к БД
         */
        connectionPool.setDataSourceName("Url list Database");
        connectionPool.setServerName(host);
        connectionPool.setPortNumber(port);
        connectionPool.setDatabaseName(dbName);
        connectionPool.setUser(username);
        connectionPool.setPassword(password);
        connectionPool.setSocketTimeout(1);
        connectionPool.setMaxConnections(maxConnections - currentConnections);
    }

    /**
     * @return Connection - Открытое соединение с базой
     * @throws SQLException
     */
    public Connection getConnection() throws SQLException {
        return connectionPool.getConnection();
    }
}
