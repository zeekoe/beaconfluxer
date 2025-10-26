package com.github.zeekoe.beaconfluxer;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.jetbrains.annotations.NotNull;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class Influx {

    private final String databaseUrl;
    private final String username;
    private final String password;

    public void writePoint(Point point) {
        InfluxDB influxDB = InfluxDBFactory.connect(databaseUrl, username, password);
        influxDB.setDatabase("sensors");
        influxDB.write(point);
    }

    public Influx() {
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream("/etc/papier.config"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        databaseUrl = properties.getProperty("influxdb.url");
        username = properties.getProperty("influxdb.username");
        password = properties.getProperty("influxdb.password");
    }

}
