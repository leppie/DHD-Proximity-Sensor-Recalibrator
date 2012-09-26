package com.leppie.dhd;

import java.util.LinkedList;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class PSService extends Service
{
  public class LocalBinder extends Binder {
    PSService getService() {
        return PSService.this;
    }
  }   
  
  private final IBinder mBinder = new LocalBinder();

  @Override
  public IBinder onBind(Intent intent)
  {
    return mBinder;
  }
  
  
  @Override
  public int onStartCommand(Intent intent, int flags, int startId)
  {
    LinkedList<String> errors = new LinkedList<String>();
    if (Calibration.Initialize(errors))
    {
      Calibration.applySaved(getBaseContext());
    }
    else
    {
      StringBuilder sb = new StringBuilder();
      for (String er : errors)
      {
        sb.append(er + "\n");
      }
      Log.e(Calibration.TAG, sb.toString());
    }
    this.stopSelf();
    return 0;
  }
  
}
