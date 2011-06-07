package com.leppie.dhd;

import java.io.IOException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class Receiver extends BroadcastReceiver
{
  @Override
  public void onReceive(Context context, Intent intent)
  {
    try
    {
      Calibration.DisablePolling();
    }
    catch (IOException e)
    {

    }
    Calibration.applySaved(context);
  }
}
