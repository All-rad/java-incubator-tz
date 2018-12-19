package bitworks.tz;

import org.pcap4j.core.*;

import java.io.IOException;

/**
 * Класс для получения статистики об использовании сети
 */
public class NetworkWatcher {
    private int trafficCounter = 0;
    private int streamSpeed = 0;

    NetworkWatcher(){
        /**
         * Получение виртуального интерфейса any через который можно отслеживать
         * весь трафик через все интерфейсы
         */
        PcapNetworkInterface netInterface = getNetworkInterface();

        /**
         * Создание обработчика прослушиваемых пакетов, при прослушке не
         * не происходит захвата содержимого пакетов, а только из подсчет
         */
        PcapHandle handle = null;
        try {
            handle = netInterface.openLive(0, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS,
                    1);
        } catch (PcapNativeException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        PacketListener listener = (PcapPacket packet) -> {
            this.trafficCounter += packet.getOriginalLength();
        };

        /**
         * Запуск прослушки и перехвата пакетов, необходимо запускать в отдельном
         * потоке так как это блокирующая операция которая может остановить
         * дальнейшее выполнение программы
         */
        PcapHandle finalHandle = handle;
        new Thread(() -> {
            try {
                finalHandle.loop(-1, listener);
            } catch (PcapNativeException | InterruptedException | NotOpenException e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }).start();

        /**
         * Запуск обработки полученной при прослушке интерфейса информации.
         * Производится вычисление скорости в Байт/с раз в 50 мс,
         * данный подсчет не точный т.к. прослушивающий обработчик не
         * успевает срабатывать на все пакеты
         */
        new Thread(() -> {
            do {
                /**
                 * Скорость вычисляется за счет деления кол-ва полученных
                 * байт за 50мс на время, после чего счетчик пакетов обнулсяется
                 */
                this.streamSpeed = this.trafficCounter / 50;
                this.trafficCounter = 0;
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (true);
        }).start();
    }

    /**
     * Метод для получения интерфейса any через который можно прослушивать
     * одновременно все интерфейсы
     * @return PcapNetworkInterface - интерфейс any
     */
    private PcapNetworkInterface getNetworkInterface() {
        PcapNetworkInterface networkInterface = null;
        try {
            for (PcapNetworkInterface item : Pcaps.findAllDevs()) {
                if (item.getName().equals("any")) {
                    networkInterface = item;
                }
            }
        } catch (PcapNativeException e) {
            e.printStackTrace();
        }
        return networkInterface;
    }

    /**
     * Метод для получения в других объектах информации о текущей скорости сети
     * @return int - скорость сети
     */
    public int getStreamSpeed() {
        return streamSpeed;
    }
}
