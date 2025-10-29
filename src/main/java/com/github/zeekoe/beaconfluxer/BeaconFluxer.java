package com.github.zeekoe.beaconfluxer;

import org.influxdb.dto.Point;
import tinyb.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/* WS08 python source used:
https://github.com/rnlgreen/thermobeacon/blob/main/thermobeacon2.py
 */

public class BeaconFluxer {
    static boolean running = true;
    private List<Beacon> beacons;

    private Lock lock;
    private Condition cv;

    /*
     * This program connects to a WS08 ThermoBeacon and reads the temperature characteristic exposed by the device over
     * Bluetooth Low Energy.
     */
    public static void main(String[] args) throws InterruptedException {
        new BeaconFluxer().run();
    }

    private void run() throws InterruptedException {
        Influx influx = new Influx();
        beacons = loadBeacons();

        discoverAndConnectBeacons();

        lock = new ReentrantLock();
        cv = lock.newCondition();

        addShutdownHook();
        fillBeacons();

        while (running) {
            Map<String, Reading> beaconReadings = new HashMap<>(beacons.size());
            for (Beacon beacon : beacons) {
                try {
                    int availableTempAndHumidityValues = getAvailableValues(beacon);
                    System.out.println("There are " + availableTempAndHumidityValues + " available data points from this device (" + beacon.bluetoothDevice().getAddress() + ")");

                    String request = buildGetLastValueRequest(availableTempAndHumidityValues);
                    byte[] response = BluetoothUtil.writeBytes(beacon.getTx(), beacon.getRx(), request);

                    Reading reading = convertToReadings(response);

                    beaconReadings.put(beacon.name(), reading);

                    System.out.println(reading);
                } catch (Exception e) {
                    e.printStackTrace();
                    // Handle exception
                }
            }

            Point.Builder pointBuilder = Point.measurement("airdata")
                    .time(LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond(), TimeUnit.SECONDS);
            beaconReadings.forEach((key, value) -> pointBuilder
                    .addField("t_" + key, value.temperature.doubleValue())
                    .addField("h_" + key, value.humidity.doubleValue()));
            influx.writePoint(pointBuilder.build());

            lock.lock();
            try {
                System.out.println("Waiting for next read...");
                cv.await(10, TimeUnit.MINUTES);
            } finally {
                lock.unlock();
            }
        }

        for (Beacon beacon : beacons) {
            beacon.bluetoothDevice().disconnect();
        }
    }

    private int getAvailableValues(Beacon beacon) {
        byte[] response = BluetoothUtil.writeBytes(beacon.getTx(), beacon.getRx(), "0100000000");
        return ((response[2] & 0xFF) << 8) | (response[1] & 0xFF);
    }

    private String buildGetLastValueRequest(int available) {
        int index = available - 1;
        // Convert index to hex, padded with leading zeroes
        String indexHex = String.format("%04x", index);
        // Reverse the byte order of the hex values
        String indexHexReversed = indexHex.substring(2) + indexHex.substring(0, 2);
        // Build the request string to be sent to the device
        return "07" + indexHexReversed + "000003";
    }

    private void fillBeacons() throws InterruptedException {
        for (Beacon beacon : beacons) {
            BluetoothDevice sensor = beacon.bluetoothDevice();
            BluetoothGattService tempService = BluetoothUtil.getService(sensor, "0000ffe0-0000-1000-8000-00805f9b34fb");

            if (tempService == null) {
                System.err.println("This device does not have the temperature service we are looking for.");
                sensor.disconnect();
                System.exit(-1);
            }
            System.out.println("Found service " + tempService.getUUID());

            BluetoothGattCharacteristic rx = BluetoothUtil.getCharacteristic(tempService, "0000fff3-0000-1000-8000-00805f9b34fb");
            BluetoothGattCharacteristic tx = BluetoothUtil.getCharacteristic(tempService, "0000fff5-0000-1000-8000-00805f9b34fb");

            if (rx == null || tx == null) {
                System.err.println("Could not find the correct characteristics.");
                sensor.disconnect();
                System.exit(-1);
            }
            beacon.setRx(rx);
            beacon.setTx(tx);
        }
    }

    private void discoverAndConnectBeacons() throws InterruptedException {
        BluetoothManager manager = BluetoothManager.getBluetoothManager();
        boolean discoveryStarted = manager.startDiscovery();
        System.out.println("Discovery started: " + (discoveryStarted ? "true" : "false"));

        for (Beacon beacon : beacons) {
            BluetoothUtil.connect(beacon);
        }

        /*
         * After we find the devices we can stop looking for other devices.
         */
        try {
            manager.stopDiscovery();
        } catch (BluetoothException e) {
            System.err.println("Discovery could not be stopped.");
        }
    }

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                running = false;
                lock.lock();
                try {
                    cv.signalAll();
                } finally {
                    lock.unlock();
                }

            }
        });
    }

    private static List<Beacon> loadBeacons() {
        List<Beacon> beacons = new ArrayList<>();

        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream("/etc/papier.config"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        String thermobeacons = properties.getProperty("airdata.thermobeacon");
        for (String beaconString : thermobeacons.split(";")) {
            String[] parts = beaconString.split(",");
            Beacon beacon = new Beacon(parts[0], parts[1]);
            System.out.println(beacon);
            beacons.add(beacon);
        }
        return beacons;
    }


    // Function to convert the readings we get back into temperatures and humidities
    public static Reading convertToReadings(byte[] response) {
        List<BigDecimal> readings = new ArrayList<>();
        for (int v = 0; v < 6; v++) {
            int resultsPosition = 6 + (v * 2);
            double reading = ((response[resultsPosition + 1] & 0xFF) << 8) | (response[resultsPosition] & 0xFF);
            reading *= 0.0625;
            if (reading > 2048) {
                reading = -1 * (4096 - reading);
            }
            readings.add(BigDecimal.valueOf(reading).setScale(2, RoundingMode.HALF_UP));
        }
        System.out.println(readings.stream().map(r -> r.toString()).collect(Collectors.joining(", ")));
        return new Reading(readings.get(0), readings.get(3));
    }

    record Reading(BigDecimal temperature, BigDecimal humidity) {
    }
}
