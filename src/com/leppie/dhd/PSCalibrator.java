package com.leppie.dhd;

import java.util.*;
import java.io.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.*;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.SeekBar.OnSeekBarChangeListener;

class Calibration
{
  static final String ps_kadc = "/sys/devices/virtual/optical_sensors/proximity/ps_kadc";
  static final String ps_polling_ignore = "/sys/devices/virtual/optical_sensors/proximity/ps_polling_ignore"; 
  static int lt = -1;
  static int ht = -1;
  static int x = -1;
  static int a = -1;
  
  static final String TAG = "DHD Proximity Recalibrator"; 
  
  public static String LastMessage;
  public static Throwable LastError;
  
  public static void applyAndSave(Context ctx, int lt, int ht)
  {
    SharedPreferences prefs = ctx.getSharedPreferences("PSCalibration", 0);
    Editor edit = prefs.edit();
    
    if (lt < 0 || ht < 0)
    {
      edit.remove("LT");
      edit.remove("HT");
    }
    else
    {
      edit.putInt("LT", lt);
      edit.putInt("HT", ht);
    }

    edit.commit();
    
    Log.i(TAG, "saving values: " + lt + " " + ht);
    
    apply(lt, ht);
    
    ShowError(ctx);
  }
  
  static void apply(int ltv, int htv)
  {
    // save to static fields here 
    lt = ltv;
    ht = htv;
    
    if (lt < 0 || ht < 0)
    {
      Log.i(TAG, "Calibration: skipping: "+ lt + " " + ht);
      return;
    }
    
    Log.i(TAG, "Calibration: applying: " + lt + " " + ht);
    
    int p1 = (0x5053 << 16) + ((lt & 0xff) << 8) + (ht & 0xff);
    int p2 = (a << 24) + (x << 16) + ((lt & 0xff) << 8) + (ht & 0xff);
    
    String output = "0x" + Integer.toHexString(p1) + " 0x" + Integer.toHexString(p2);
    
    Log.i(TAG, "Writing to ps_kadc: " + output);
    
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
      lt = -1; ht = -1;
      LastError = e;
      LastMessage = "Error when writing to ps_kadc";
      Log.e(TAG, LastMessage, LastError);
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
    
    Log.i(TAG, "loading values: " + lt + " " + ht);
    
    if (!(lt < 0 || ht < 0))
    {
      applyAndNotify(ctx, lt, ht);
    }
  }
  
  static String Prettify(Throwable e)
  {
    StringBuilder sb = new StringBuilder(e.toString());
    for (StackTraceElement se : e.getStackTrace())
    {
      sb.append("\n" + se.toString());
    }
    return sb.toString();
  }
  
  public static boolean ShowError(Context ctx)
  {
    if (LastError != null)
    {
      AlertDialog alertDialog = new AlertDialog.Builder(ctx).create();
      alertDialog.setTitle(LastMessage);
      alertDialog.setMessage(Prettify(LastError));
      alertDialog.show();
      LastMessage = null;
      LastError = null;
      return true;
    }
    return false;
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
  public static void getValues()
  {
    try
    {
      FileReader fr = new FileReader(ps_kadc);
      char[] buf = new char[256];
      int len = fr.read(buf);
      String str = new String(buf, 0, len);
      Log.i(TAG, "Reading from ps_kadc: " + str);
      StringTokenizer tokens = new StringTokenizer(str, "=");
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
      lt = -1; ht = -1;
      LastError = e;
      LastMessage = "ps_kadc file not found";
      Log.e(TAG, LastMessage, LastError);
    }
    catch (IOException e)
    {
      lt = -1; ht = -1;
      LastError = e;
      LastMessage = "Error reading ps_kadc";
      Log.e(TAG, LastMessage, LastError);
    }
  }
  
  public static boolean Initialize(LinkedList<String> errors)
  {
    File f_ps_kadc = new File(ps_kadc);
    File f_ps_polling_ignore = new File(ps_polling_ignore);
    
    if (!f_ps_kadc.exists()) 
    {
      errors.add("- ps_kadc not found");
      return false;
    }
    if (!f_ps_polling_ignore.exists()) 
    {
      errors.add("- ps_polling_ignore not found");
      return false;
    }
    
    if (!f_ps_kadc.canWrite())
    {
      // super user?
      errors.add("- ps_kadc not writable");
      return false;
    }
    
    if (!f_ps_polling_ignore.canWrite())
    {
      // super user?
      errors.add("- ps_polling_ignore not writable");
      return false;
    }
    else
    {
      // disable polling
      try
      {
        DisablePolling();
      }
      catch (IOException e)
      {
        errors.add(e.getMessage());
        return false;
      }
    }
    
    return true;
  }

  public static void DisablePolling()
      throws IOException
  {
    FileWriter fw = new FileWriter(ps_polling_ignore);
    fw.write("0");
    fw.close();
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

    LinkedList<String> errors = new LinkedList<String>();
    
    if (!Calibration.Initialize(errors))
    {
      AlertDialog alertDialog = new AlertDialog.Builder(this).create();
      alertDialog.setTitle("Kernel not supported");
      StringBuilder sb = new StringBuilder("Please use the latest CM7 kernel\nErrors:\n");
      for (String er : errors)
      {
        sb.append(er + "\n");
      }
      alertDialog.setMessage(sb.toString());
      final Activity app = this;
      alertDialog.setButton("Quit", new DialogInterface.OnClickListener()
      {
        @Override
        public void onClick(DialogInterface dialog, int which)
        {
          app.finish();
        }
      });
      
      alertDialog.show();
      return;
    }
    
    context = this;
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
          UpdateCalibrationValues();
        }
        else
        {
          sensormanager.registerListener(psevent, ps, SensorManager.SENSOR_DELAY_FASTEST);
          setPSStatus("Waiting for reading");
          applybut.setText("Stop");
          UpdateCalibrationValues();
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
    
    int lt = Calibration.getLT();
    int ht = Calibration.getHT();
    
    if (Calibration.ShowError(this))
    {
    }
    else
    {
      if (lt < 0 || ht < 0)
      {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("Error");
        alertDialog.setMessage("Program not working correctly, check logcat");
        alertDialog.show();
      }
      else
      {
        UpdateCalibrationValues(lt, ht);
      }
    }
  }
  
  void UpdateCalibrationValues(int lt, int ht)
  {
    SeekBar ltslider = (SeekBar) findViewById(R.id.lt_slider);
    SeekBar htslider = (SeekBar) findViewById(R.id.ht_slider);
    
    TextView lt_value = (TextView) findViewById(R.id.lt_value);
    TextView ht_value = (TextView) findViewById(R.id.ht_value);
    
    lt_value.setText(new Integer(lt).toString());
    ht_value.setText(new Integer(ht).toString());
    
    ltslider.setProgress(lt);
    htslider.setProgress(ht);
  }
  
  void UpdateCalibrationValues()
  {
    Calibration.getValues();
    UpdateCalibrationValues(Calibration.getLT(), Calibration.getHT());
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
    float val = event.values[0]; 
    String pval =  val == 0.0 ? "NEAR" : val > 0 ?  "FAR" : "UKNOWN";
    setPSStatus(pval);
    
    UpdateCalibrationValues();
  }
  
}

