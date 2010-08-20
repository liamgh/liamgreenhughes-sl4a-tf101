package com.googlecode.android_scripting.facade;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;

import com.googlecode.android_scripting.jsonrpc.RpcReceiver;
import com.googlecode.android_scripting.rpc.Rpc;
import com.googlecode.android_scripting.rpc.RpcEvent;
import com.googlecode.android_scripting.rpc.RpcMinSdk;

import java.lang.reflect.Field;

public class BatteryManagerFacade extends RpcReceiver {
  private final Service mService;
  private final BatteryStateListener mReceiver;

  private volatile Integer mBatteryStatus = null;
  private volatile Integer mBatteryHealth = null;
  private volatile Integer mPlugType = null;

  private volatile Boolean mBatteryPresent = null;
  private volatile Integer mBatteryLevel = null;
  private volatile Integer mBatteryMaxLevel = null;
  private volatile Integer mBatteryVoltage = null;
  private volatile Integer mBatteryTemperature = null;
  private volatile String mBatteryTechnology = null;

  private volatile Bundle mBatteryData = null;

  private int mSdkVersion = 3;

  public BatteryManagerFacade(FacadeManager manager) {
    super(manager);
    mService = manager.getService();
    mSdkVersion = manager.getSdkLevel();
    mReceiver = new BatteryStateListener(manager.getReceiver(EventFacade.class));
    mBatteryData = new Bundle();
  }

  private class BatteryStateListener extends BroadcastReceiver {

    private final EventFacade mmEventFacade;

    private BatteryStateListener(EventFacade facade) {
      mmEventFacade = facade;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      mBatteryStatus = intent.getIntExtra("status", 1);
      mBatteryHealth = intent.getIntExtra("health", 1);
      mPlugType = intent.getIntExtra("plugged", -1);
      if (mSdkVersion >= 5) {
        mBatteryPresent =
            intent.getBooleanExtra(getBatteryManagerFieldValue("EXTRA_PRESENT"), false);
        mBatteryLevel = intent.getIntExtra(getBatteryManagerFieldValue("EXTRA_LEVEL"), -1);
        mBatteryMaxLevel = intent.getIntExtra(getBatteryManagerFieldValue("EXTRA_SCALE"), 0);
        mBatteryVoltage = intent.getIntExtra(getBatteryManagerFieldValue("EXTRA_VOLTAGE"), -1);
        mBatteryTemperature =
            intent.getIntExtra(getBatteryManagerFieldValue("EXTRA_TEMPERATURE"), -1);
        mBatteryTechnology = intent.getStringExtra(getBatteryManagerFieldValue("EXTRA_TECHNOLOGY"));
      }
      Bundle data = new Bundle();
      data.putInt("status", mBatteryStatus);
      data.putInt("health", mBatteryHealth);
      data.putInt("plugged", mPlugType);
      data.putBoolean("battery_present", mBatteryPresent);
      if (mBatteryMaxLevel == null || mBatteryMaxLevel == 100 || mBatteryMaxLevel == 0) {
        data.putInt("level", mBatteryLevel);
      } else {
        data.putInt("level", (int) (mBatteryLevel * 100.0 / mBatteryMaxLevel));
      }
      data.putInt("voltage", mBatteryVoltage);
      data.putInt("temperature", mBatteryTemperature);
      data.putString("technology", mBatteryTechnology);
      mBatteryData = data;
      mmEventFacade.postEvent("battery", mBatteryData);
    }
  }

  private String getBatteryManagerFieldValue(String name) {
    try {
      Field f = BatteryManager.class.getField(name);
      return f.get(null).toString();
    } catch (Exception e) {
    }
    return null;
  }

  @Rpc(description = "Returns the most recently recorded battery data.")
  public Bundle readBatteryData() {
    return mBatteryData;
  }

  @RpcEvent("battery")
  @Rpc(description = "Starts tracking battery state.")
  public void batteryStartMonitoring() {
    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_BATTERY_CHANGED);
    mService.registerReceiver(mReceiver, filter);
  }

  @Rpc(description = "Stops tracking battery state.")
  public void batteryStopMonitoring() {
    mService.unregisterReceiver(mReceiver);
  }

  @Override
  public void shutdown() {
    mService.unregisterReceiver(mReceiver);
  }

  @Rpc(description = "Returns  the most recently received battery status data:" + "\n1 - unknown;"
      + "\n2 - charging;" + "\n3 - discharging;" + "\n4 - not charging;" + "\n5 - full;")
  public Integer batteryGetStatus() {
    return mBatteryStatus;
  }

  @Rpc(description = "Returns the most recently received battery health data:" + "\n1 - unknown;"
      + "\n2 - good;" + "\n3 - overheat;" + "\n4 - dead;" + "\n5 - over voltage;"
      + "\n6 - unspecified failure;")
  public Integer batteryGetHealth() {
    return mBatteryHealth;
  }

  /** Power source is an AC charger. */
  public static final int BATTERY_PLUGGED_AC = 1;
  /** Power source is a USB port. */
  public static final int BATTERY_PLUGGED_USB = 2;

  @Rpc(description = "Returns the most recently received plug type data:" + "\n-1 - unknown;"
      + "\n0 - unplugged;" + "\n1 - power source is an AC charger;"
      + "\n2 - power source is a USB port;")
  public Integer batteryGetPlugType() {
    return mPlugType;
  }

  @Rpc(description = "Returns the most recently received battery presence data")
  @RpcMinSdk(5)
  public Boolean batteryCheckPresent() {
    return mBatteryPresent;
  }

  @Rpc(description = "Returns the most recently received battery level (percentage)")
  @RpcMinSdk(5)
  public Integer batteryGetLevel() {
    if (mBatteryMaxLevel == null || mBatteryMaxLevel == 100 || mBatteryMaxLevel == 0) {
      return mBatteryLevel;
    } else {
      return (int) (mBatteryLevel * 100.0 / mBatteryMaxLevel);
    }
  }

  @Rpc(description = "Returns the most recently received battery voltage")
  @RpcMinSdk(5)
  public Integer batteryGetVoltage() {
    return mBatteryVoltage;
  }

  @Rpc(description = "Returns the most recently received battery temperature")
  @RpcMinSdk(5)
  public Integer batteryGetTemperature() {
    return mBatteryTemperature;
  }

  @Rpc(description = "Returns the most recently received battery technology data")
  @RpcMinSdk(5)
  public String batteryGetTechnology() {
    return mBatteryTechnology;
  }

}
