package com.leppie.dhd;

import java.util.*;
import java.io.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.*;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.SeekBar.OnSeekBarChangeListener;

class Calibration
{
  static final String ps_kadc = "/sys/devices/virtual/optical_sensors/proximity/ps_kadc"; 
  static int lt = -1;
  static int ht = -1;
  static int x = -1;
  static int a = -1;
  
  public static void applyAndSave(Context ctx, int lt, int ht)
  {
    SharedPreferences prefs = ctx.getSharedPreferences("PSCalibration", 0);
    Editor edit = prefs.edit();
    edit.putInt("LT", lt);
    edit.putInt("HT", ht);
    edit.commit();
    
    Log.i("saving values:", lt + " " + ht);
    
    apply(lt, ht);
  }
  
  static void apply(int ltv, int htv)
  {
    // save to static fields here 
    lt = ltv;
    ht = htv;
    
    Log.i("Calibration", lt + " " + ht);
    
    int p1 = (0x5053 << 16) + ((lt & 0xff) << 8) + (ht & 0xff);
    int p2 = (a << 24) + (x << 16) + ((lt & 0xff) << 8) + (ht & 0xff);
    
    String output = "0x" + Integer.toHexString(p1) + " 0x" + Integer.toHexString(p2);
    
    Log.i("Writing", output);
    
    FileWriter fw;

    try
    {
      fw = new FileWriter(ps_kadc);
      fw.write(output);
      fw.write("\n");
      fw.flush();
      fw.close();
    }
    catch (IOException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  
  static void applyAndNotify(Context ctx, int lt, int ht)
  {
    apply(lt, ht);
    Toast.makeText(ctx, "Proximity sensor recalibrated: LT=" + lt + " HT=" + ht, Toast.LENGTH_LONG).show();
  }

  public static void applySaved(Context ctx)
  {
    SharedPreferences prefs = ctx.getSharedPreferences("PSCalibration", 0);
    int lt = prefs.getInt("LT", -1);
    int ht = prefs.getInt("HT", -1);
    
    Log.i("loading values:", lt + " " + ht);
    
    if (!(lt < 0 || ht < 0))
    {
      applyAndNotify(ctx, lt, ht);
    }
  }
  
  public static int getLT()
  {
    if (lt < 0 || ht < 0)
    {
      getValues();
    }
    return lt;
  }
  
  public static int getHT()
  {
    if (lt < 0 || ht < 0)
    {
      getValues();
    }
    return ht;
  }
  
  @SuppressWarnings("unused")
  static void getValues()
  {
    try
    {
      FileReader fr = new FileReader(ps_kadc);
      char[] buf = new char[256];
      int len = fr.read(buf);
      StringTokenizer tokens = new StringTokenizer(new String(buf, 0, len), "=");
      tokens.nextToken();
      String values = tokens.nextToken().trim();
      values = values.substring(1, values.length() - 1);
      tokens = new StringTokenizer(values, ", ");
      
      String B_value, C_value, A_value, X_value, THL, THH;
      
      B_value = tokens.nextToken();
      C_value = tokens.nextToken();
      A_value = tokens.nextToken();
      X_value = tokens.nextToken();
      THL = tokens.nextToken();
      THH = tokens.nextToken();
      
      a = Integer.parseInt(A_value.substring(2), 16) & 0xff;
      x = Integer.parseInt(X_value.substring(2), 16) & 0xff;
      lt = Integer.parseInt(THL.substring(2), 16);
      ht = Integer.parseInt(THH.substring(2), 16);
    }
    
    catch (FileNotFoundException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    catch (IOException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
}

public class PSCalibrator extends Activity implements SensorEventListener
{
  SensorManager sensormanager;
  Sensor ps;
  SensorEventListener psevent;
  Context context;
  boolean running = false;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    
    context = getApplicationContext();
    psevent = this;
    
    sensormanager = (SensorManager)getSystemService(SENSOR_SERVICE);
    
    ps = sensormanager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
    
    final Button applybut = (Button) findViewById(R.id.applyBut); 
    
    applybut.setOnClickListener(new OnClickListener()
    {
      @Override
      public void onClick(View v)
      {
        if (running)
        {
          sensormanager.unregisterListener(psevent);
          setPSStatus("Not running");
          applybut.setText("Start");
        }
        else
        {
          sensormanager.registerListener(psevent, ps, SensorManager.SENSOR_DELAY_FASTEST);
          setPSStatus("Waiting for reading");
          applybut.setText("Stop");
        }
        running = !running;
      }
    });
    
    final SeekBar ltslider = (SeekBar) findViewById(R.id.lt_slider);
    final SeekBar htslider = (SeekBar) findViewById(R.id.ht_slider);
    
    final TextView lt_value = (TextView) findViewById(R.id.lt_value);
    final TextView ht_value = (TextView) findViewById(R.id.ht_value);
    
    ltslider.setOnSeekBarChangeListener(new OnSeekBarChangeListener()
    {
      @Override
      public void onStopTrackingTouch(SeekBar seekBar)
      {
        Calibration.applyAndSave(context, ltslider.getProgress(), htslider.getProgress());
      }
      
      @Override
      public void onStartTrackingTouch(SeekBar seekBar)
      {
      }
      
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
      {
        lt_value.setText(new Integer(progress).toString());
        if (progress >= (htslider.getProgress() - 1))
        {
          htslider.setProgress(progress + 1);
        }
      }
    });
    
    htslider.setOnSeekBarChangeListener(new OnSeekBarChangeListener()
    {
      @Override
      public void onStopTrackingTouch(SeekBar seekBar)
      {
        Calibration.applyAndSave(context, ltslider.getProgress(), htslider.getProgress());
      }
      
      @Override
      public void onStartTrackingTouch(SeekBar seekBar)
      {
      }
      
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
      {
        ht_value.setText(new Integer(progress).toString());
        if (progress <= (ltslider.getProgress() + 1))
        {
          ltslider.setProgress(progress - 1);
        }

      }
    });
    
    ZoomControls ltzoom = (ZoomControls) findViewById(R.id.ltZoom); 
    ZoomControls htzoom = (ZoomControls) findViewById(R.id.htZoom);
    
    ltzoom.setOnZoomInClickListener(new OnClickListener()
    {
      @Override
      public void onClick(View v)
      {
        ltslider.setProgress(ltslider.getProgress() + 1);
        Calibration.applyAndSave(context, ltslider.getProgress(), htslider.getProgress());
      }
    });
    
    ltzoom.setOnZoomOutClickListener(new OnClickListener()
    {
      @Override
      public void onClick(View v)
      {
        ltslider.setProgress(ltslider.getProgress() - 1);
        Calibration.applyAndSave(context, ltslider.getProgress(), htslider.getProgress());
      }
    });
    
    htzoom.setOnZoomInClickListener(new OnClickListener()
    {
      @Override
      public void onClick(View v)
      {
        htslider.setProgress(htslider.getProgress() + 1);
        Calibration.applyAndSave(context, ltslider.getProgress(), htslider.getProgress());
      }
    });
    
    htzoom.setOnZoomOutClickListener(new OnClickListener()
    {
      @Override
      public void onClick(View v)
      {
        htslider.setProgress(htslider.getProgress() - 1);
        Calibration.applyAndSave(context, ltslider.getProgress(), htslider.getProgress());
      }
    });
    
    Button b = (Button) findViewById(R.id.beerbut);
    
    b.setOnClickListener(new OnClickListener()
    {
      @Override
      public void onClick(View v)
      {
        Uri uri = Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=xacc.ide@gmail.com" + 
            "&item_name=DHD%20Proximity%20Recalibrator&no_shipping=1&amount=2&currency_code=USD" );
        startActivity(new Intent( Intent.ACTION_VIEW, uri ) );
      }
    });
    
    
    int lt = Calibration.getLT();
    int ht = Calibration.getHT();
    
    lt_value.setText(new Integer(lt).toString());
    ht_value.setText(new Integer(ht).toString());
    
    ltslider.setProgress(lt);
    htslider.setProgress(ht);
  }
  
  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy)
  {
  }
  
  void setPSStatus(String val)
  {
    TextView psstatus = (TextView) findViewById(R.id.ps_status);
    psstatus.setText(val);
  }

  @Override
  public void onSensorChanged(SensorEvent event)
  {
    String pval =  event.values[0] == 0.0 ? "NEAR" : "FAR";
    Log.i("Proximity changed", pval);
    setPSStatus(pval);
  }
  
}

