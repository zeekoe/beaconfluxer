package com.github.zeekoe.beaconfluxer;

import tinyb.BluetoothDevice;
import tinyb.BluetoothGattCharacteristic;

import java.util.Objects;

final class Beacon {
  private final String name;
  private final String address;
  private BluetoothDevice bluetoothDevice;
  private BluetoothGattCharacteristic rx;
  private BluetoothGattCharacteristic tx;

  Beacon(String name, String address) {
    this.name = name;
    this.address = address;
  }

  public String name() {
    return name;
  }

  public String address() {
    return address;
  }

  public BluetoothDevice bluetoothDevice() {
    return bluetoothDevice;
  }

  public void setBluetoothDevice(BluetoothDevice bluetoothDevice) {
    this.bluetoothDevice = bluetoothDevice;
  }

  public BluetoothGattCharacteristic getRx() {
    return rx;
  }

  public void setRx(BluetoothGattCharacteristic rx) {
    this.rx = rx;
  }

  public BluetoothGattCharacteristic getTx() {
    return tx;
  }

  public void setTx(BluetoothGattCharacteristic tx) {
    this.tx = tx;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (Beacon) obj;
    return Objects.equals(this.name, that.name) &&
        Objects.equals(this.address, that.address) &&
        Objects.equals(this.bluetoothDevice, that.bluetoothDevice);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, address, bluetoothDevice);
  }

  @Override
  public String toString() {
    return "Beacon[" +
        "name=" + name + ", " +
        "address=" + address + ", " +
        "bluetoothDevice=" + bluetoothDevice + ']';
  }

}
