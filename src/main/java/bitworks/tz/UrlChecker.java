package bitworks.tz;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.Request;
import org.asynchttpclient.Response;
import org.pcap4j.core.PcapNativeException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.*;

/**
 * Класс для проверки статусов сайтов по URL
 */
public class UrlChecker {
    private AsyncHttpClient asyncHttpClient;
    private DatabaseClient databaseConnectionPool;
    private NetworkWatcher networkWatcher;
    private String tableName;

    UrlChecker(DatabaseClient databaseClient) throws PcapNativeException {
        /**
         * Создание объекта http клиента
         * клиента для работы с БД и объекта
         * следящего за состоянием сети
         */
        asyncHttpClient = Dsl.asyncHttpClient();
        databaseConnectionPool = databaseClient;
        networkWatcher = new NetworkWatcher();
    }

    /**
     * Создает и управляет пулом потоков в которых происходит проверка
     * статуса по url и результат записывается в БД
     * @param urls - список проверяемых url-ов
     * @throws InterruptedException
     */
    private void createThreadPool(UrlInfo[] urls) throws InterruptedException {
        int maxSpeed = 0;
        int currentSpeed = 0;

        /**
         * Создание пула потоков в котором старновый размер
         * пула 10 доступных потоков.
         * Максимальное число потоков - 4000
         */
        ThreadPoolExecutor executorPool = new ThreadPoolExecutor(
                10,
                4000,
                12,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(5000),
                Executors.defaultThreadFactory()
        );

        /**
         * Итератор по списку url-ов
         * Все полученные url-ы добавляются в списко задач
         * на исполнение в пуле потоков
         */
        for (UrlInfo item : urls) {
            executorPool.execute(() -> {
                Connection connection = null;
                Statement statement = null;
                String url;
                /**
                 * Если url в БД был записан без указания протокола,
                 * то он добавляется, т.к. используемых http клиет требует
                 * указание протокола соединения
                 */
                if (item.url.contains("http://") || item.url.contains("https://")) {
                    url = item.url;
                } else {
                    url = "http://" + item.url;
                }

                /**
                 * Вызов метода который производит запрос и возвращает его статус,
                 * тайм-аут запроса 6 секунд
                 */
                int status = getUrlStatus(url, 6000);

                /**
                 * Получение коннекта из пула для соединения с БД
                 */
                try {
                    connection =  databaseConnectionPool.getConnection();
                    statement = connection.createStatement();
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                String query = String.format("UPDATE %s SET status=%d WHERE id=%d",
                        tableName,
                        status,
                        item.id);
                /**
                 * Выполнение запроса на обновление статуса в записи таблицы
                 */
                try {
                    assert statement != null;
                    statement.executeUpdate(query);
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                /**
                 * Закрытие соединения для его освобождения и возврата в
                 * пул соединений к базе
                 */
                try {
                    statement.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                try {
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        }

        /**
         * Цикл который ожидает завершения всех операций в потоках
         * Также данный цикл отслеживает нагрузку на сеть и по
         * приращению пропускаемого трафика определяет на сколько
         * нагружена сеть и можно ли увеличить максимальное число потоков
         * без её перегрузки. Данная проверка проводится раз в 100мс
         */
        while (executorPool.getActiveCount() > 0) {
            currentSpeed = networkWatcher.getStreamSpeed();
            if (currentSpeed > maxSpeed) {
                executorPool.setCorePoolSize(executorPool.getCorePoolSize() + 20);
                maxSpeed = currentSpeed;
            }
            Thread.sleep(100);
        }
    }

    /**
     * Метод для проверки статуса запроса по указанному URL
     * @param url - url для проверки статуса
     * @param timeout - тайм-аут запроса, при превышении данного времени
     *                будет возвращен статус 408 (Connection Timeout)
     * @return int - статус запроса
     */
    public int getUrlStatus(String url, int timeout) {
        int result = 0;
        /**
         * Создается запрос с переданными параметрами
         */
        Request request = Dsl.get(url)
                .setRequestTimeout(timeout)
                .build();
        /**
         * Выполнение самого запроса
         */
        Future<Response> response = asyncHttpClient.executeRequest(request);
        try {
            result = response.get().getStatusCode();
        } catch (InterruptedException | ExecutionException e) {
            result = 408;
        }

        return result;
    }

    /**
     *
     * @param tableName - имя таблицы БД в которой содержатся данные
     * @param date - дата старше которой все записи обновляются
     * @throws SQLException
     * @throws ParseException
     * @throws InterruptedException
     */
    public void startCheck(String tableName, Date date) throws SQLException, ParseException, InterruptedException {

        this.tableName = tableName;

        Connection conn = this.databaseConnectionPool.getConnection();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String stringDate = sdf.format(date);

        /**
         * Получение кол-ва устаревших записей в базе, необходимо
         * для последующей корректной
         */
        String query = String.format("SELECT Count(*) FROM %s WHERE date<'%s'", tableName, stringDate);
        Statement statement = conn.createStatement();
        ResultSet rs = statement.executeQuery(query);
        rs.next();
        int recordNum = rs.getInt(1);
        rs.close();

        /**
         * Запрос подходящих про критерию url-ов из базы
         * Получение происходит частсями по 2000 записей чтобы
         * не упереться в лимит оперативной памяти машины при загрузке
         * всех записей за раз
         */
        query = String.format("SELECT * FROM %s WHERE date<'%s' LIMIT 2000 OFFSET ", tableName, stringDate);
        for (int i = 0; i < recordNum / 2000 + 1; i++) {
            /**
             * i - определяет номер получаемой страницы списка
             */
            rs = statement.executeQuery(query + i);
            /**
             * Все записи из базы данных помещаются в список из структур их описывающих
             */
            ArrayList<UrlInfo> urlsInfoList = new ArrayList<>();
            while (rs.next()) {
                UrlInfo urlInfo = new UrlInfo(
                    rs.getInt("id"),
                    rs.getString("url"),
                    sdf.parse(rs.getString("date").split(" ")[0]),
                    rs.getInt("status")
                );
                urlsInfoList.add(urlInfo);
            }
            /**
             * После получения записей вызывается метод который создает и управляет
             * пулом потоков в котором проверяются url-ы
             */
            createThreadPool(urlsInfoList.toArray(new UrlInfo[urlsInfoList.size()]));
        }
    }
}
