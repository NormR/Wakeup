package com.normsstuff.wakeup;


import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.Set;

import com.normstools.SaveStdOutput;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.preference.PreferenceManager;

public class MainActivity extends Activity {
	// See VersionInfo to set the Version

	String rootDir = Environment.getExternalStorageDirectory().getPath();
	final String LogFilePathPfx = rootDir + "/WakeUp_log_";
	
    boolean saveSTDOutput = false;    // flag 
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HHmmss", Locale.US); // builds Filename
    SimpleDateFormat sdfTime = new SimpleDateFormat("yyyy-MM-dd 'T' HH:mm:ss", Locale.US); // showTime

    //-------------------------------------------------
	private static final int RESULT_SETTINGS = 31;
    final int GetTimeID = 234;
    
	//  Times for alarms
	final static String STime = "STime";
	int startHour = 6;
	int startMinute = 30;
	int stopHour = 22;
	int stopMinute = 0;


	//  For detecting orientation of device
    boolean runningSensor = false;
    boolean debugging = false;
    private int buildLvl = -99;
    
    boolean addNotify = false;
    final String AddNotify_B = "AddNotify"; 		// for bundle access
    
    final static String AddNotify = "AddNotify";    // Key for bundle value
    final public static String FirstStart = "FrstStrt";  // when Service first started
    
    //============================================================  
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	    System.out.println("WakeUp onCreate() sIS="+savedInstanceState
	    		+ " at " +sdfTime.format(new java.util.Date()));
		
		runningSensor = isMyServiceRunning(ScreenOnOffService.class);
		buildLvl = Build.VERSION.SDK_INT;  // need this for AlarmManager
		
		final Button startSensorBtn = (Button) findViewById(R.id.startSensor);
		String btnText = (runningSensor ? "Stop" : "Start") + " WakeUp Service";
		startSensorBtn.setText(btnText);
		
		// This Intent is for starting the Service that will do the job
		final Intent intent2 = new Intent(MainActivity.this, ScreenOnOffService.class); 
		
		startSensorBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// Start Orientation sensor
		    	if(!runningSensor) {
		    		runningSensor = true;
		    	    stopService(new Intent(MainActivity.this, ScreenOnOffService.class));
		    	    
		    		// Start a service to keep this thing alive
		    		intent2.putExtra(FirstStart, true);   // Tell Service its the first Intent
		    		intent2.putExtra(AddNotify, addNotify);
		    		putTimesInIntent(intent2); // insert the times as extra data
		    		startService(intent2);
		    		System.out.println("WU started ScrnOnOffSrvc at "+ sdfTime.format(new java.util.Date()));

//		    	    showMsg("Service with Sensor started");
		    	    startSensorBtn.setText("Stop WakeUp Service"); //  change text of button's label
		    	    
		    	}else {
		    		//  Turn it off if it's running
		    		runningSensor = false;
//		    	    sensorManager.unregisterListener(sensorEventListener);
		    		System.out.println("WU OnOffSrvc running="+ isMyServiceRunning(ScreenOnOffService.class)); 
		    		stopService(intent2);
		    		showMsg("Service with Sensor stopped");
		    	    startSensorBtn.setText("Start WakeUp Service"); //  change text of button's label
		    		
		    		System.out.println("WU OnOffSrvc after running="+ isMyServiceRunning(ScreenOnOffService.class)); 
				}		

				
			}
		});  // end setting click listener
		
		setTextViewTexts();  // Read preferences and set: text views, debugging and addNotify
		
		// Is this a restart?
		if (savedInstanceState != null) {
//			addNotify = savedInstanceState.getBoolean(AddNotify_B);
		}
		
		if(debugging) {
       	 	// Quick and dirty debugging
            java.util.Date dt =  new java.util.Date();
            String fn = LogFilePathPfx + sdf.format(dt) + ".txt";   // 2014-02-02T193504

            try {
   				SaveStdOutput.start(fn);
   				saveSTDOutput = true;    // remember
	   	    } catch (IOException e) {
	   			e.printStackTrace();
	   	    }

		}
		
       	Intent intent = getIntent();
    	System.out.println("WU onCreate() intent="+intent 
    			+ "\n >>data="+intent.getData()
    			+ "\n savedInstanceState=" + savedInstanceState
    			+ "\n >>extras="+ intent.getExtras());

        // Were we started by an Intent?
		Bundle bndl = intent.getExtras();
		if(bndl != null){
    		Set<String> set = bndl.keySet();
    		System.out.println(" >>bndl keySet=" + Arrays.toString(set.toArray()));
        }	
		
	}  //  end onCreate()

	//---------------------------------------------------------------
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
	    switch (item.getItemId()) {	
	    case R.id.action_settings:
			// Starts the Settings activity on top of the current activity
			Intent intent = new Intent(this, SettingsActivity.class);
			startActivityForResult(intent, RESULT_SETTINGS);
			return true;
			
	    case R.id.set_times:
	    	Intent intPT = new Intent(this, GetStopStartTimesActivity.class); 
	    	putTimesInIntent(intPT);
	    	startActivityForResult(intPT, GetTimeID);
	    	return true;
			
        case R.id.about:
            showMsg("Norm's WakeUp program\n"
            		+ VersionInfo.Version
            		+ "email: radder@hotmail.com");
            return true;

			
        case R.id.exit:
        	finish();
        	return true;
	    	
	   	default:
	   		break;

		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu m) {
		super.onPrepareOptionsMenu(m);
/*		
		MenuItem mi = (MenuItem)m.findItem(R.id.addnotify);
		if(mi != null)                  //<<<<<<<<<< null???
			mi.setChecked(addNotify);
		else
			System.out.println("onPrepOptsMenu() mi=null  menu="+m);
*/			
		return true;
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		System.out.println("onActRes() intent=" + data);

		switch (requestCode) {
		case RESULT_SETTINGS:
			setTextViewTexts();
			break;
			
		case GetTimeID:
			try {
				//  Does start time have to be before stop time???? <<<<<<<<<<<<
				startMinute = data.getIntExtra(GetStopStartTimesActivity.Minute_S, 30);
				startHour = data.getIntExtra(GetStopStartTimesActivity.Hour_S, 6);
				stopMinute = data.getIntExtra(GetStopStartTimesActivity.Minute_E, 0);
				stopHour = data.getIntExtra(GetStopStartTimesActivity.Hour_E, 22);
				System.out.println("WU >>>  Stop hour="+stopHour + ", min="+stopMinute
									+"  Start: hour="+startHour+", min="+startMinute);
				setStopStartTV();  // go show the new values
				
				// Now save the new values in Preferences?
				SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
				editor.putInt(GetStopStartTimesActivity.Hour_S, startHour);
				editor.putInt(GetStopStartTimesActivity.Minute_S, startMinute);
				editor.putInt(GetStopStartTimesActivity.Hour_E, stopHour);
				editor.putInt(GetStopStartTimesActivity.Minute_E, stopMinute);
				editor.commit();

			}catch(Exception x){
				x.printStackTrace();
			}

			break;

		}

	}

	
	//--------------------------------------------------------------
	//  Save values to allow us to restore state when rotated
	@Override
	public void onSaveInstanceState(Bundle bndl) {
		super.onSaveInstanceState(bndl);
		bndl.putBoolean(AddNotify_B, addNotify);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		System.out.println("WU onPause() at "+ sdfTime.format(new java.util.Date()));
	}
	
    @Override
    public void onDestroy() {
    	super.onDestroy();

		if(runningSensor) {
//		    sensorManager.unregisterListener(sensorEventListener);  //NO DO NOT STOP HERE!!!!
		}
		System.out.println("WU onDestroy at "+ sdfTime.format(new java.util.Date()));
		if(saveSTDOutput)  { // was it started?
//			SaveStdOutput.stop();  //<<<<<<<<<<< DON'T stop others using it!!!
		}
    }
    
	//-----------------------------------------------------------------
	private boolean isMyServiceRunning(Class<?> serviceClass) {
	    ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if (serviceClass.getName().equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}

	// Put the Stop/Start times into an Intent
	private void putTimesInIntent(Intent intent){
    	intent.putExtra(GetStopStartTimesActivity.Hour_S, startHour);  // Pass current values
    	intent.putExtra(GetStopStartTimesActivity.Minute_S, startMinute);
    	intent.putExtra(GetStopStartTimesActivity.Hour_E, stopHour); 
    	intent.putExtra(GetStopStartTimesActivity.Minute_E, stopMinute);
	}
	
	//--------------------------------------------------------------------------
	// Set the texts AND the values of debugging and addNotify from preferences
	private void setTextViewTexts() {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		debugging = preferences.getBoolean("set_debug_text", false);

		TextView tv = (TextView)findViewById(R.id.message1);
		tv.setText("Debug is "+(debugging ? "On" : "Off"));
		
		addNotify = preferences.getBoolean("set_notify", false);
		TextView tv2 = (TextView)findViewById(R.id.message2);
		tv2.setText("Set Notify is "+(addNotify ? "On" : "Off"));
		
		String printOutFreq = preferences.getString("frequencyOfPO", "1000");
		String sensorCount = preferences.getString("sensorCounts", "1");
		System.out.println("WU sTVT printOut freq="+printOutFreq + ", sensorCount="
							+ sensorCount);
		
		startHour = preferences.getInt(GetStopStartTimesActivity.Hour_S, 6);
		startMinute = preferences.getInt(GetStopStartTimesActivity.Minute_S, 30);
		stopHour = preferences.getInt(GetStopStartTimesActivity.Hour_E, 22);
		stopMinute = preferences.getInt(GetStopStartTimesActivity.Minute_E, 0);
		
		setStopStartTV();
		
		// Show some debug stuff
		TextView tv4 = (TextView)findViewById(R.id.message4); 
		tv4.setText("Build level="+buildLvl);
	}
	
	private void setStopStartTV() {
		TextView tv3 = (TextView)findViewById(R.id.message3);
		String ssTimes = "Stop at " + build_HHMM(stopHour, stopMinute) 
						+ "   Start at " + build_HHMM(startHour, startMinute);
		tv3.setText(ssTimes);
	}

    /**
    * Sets up the alarm
    *
    * @param seconds
    *            - after how many seconds from now the alarm should go off
    */
   @TargetApi(Build.VERSION_CODES.M)    // allow M code
    private void setupAlarm(int seconds) {
      AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
      Intent intent = new Intent(getBaseContext(), OnAlarmReceive.class);
      PendingIntent pendingIntent = PendingIntent.getBroadcast(
                     this, 0, intent,
                     PendingIntent.FLAG_UPDATE_CURRENT);
     
      System.out.println("Setup the alarm for " + seconds + " seconds.");
     
      // Getting current time and add the seconds in it
      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.SECOND, seconds);
      // Marshmallow needs better method
      if(buildLvl >= Build.VERSION_CODES.M) {
       	  alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
//    	  alarmManager.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
      } else {
    	  alarmManager.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
      }
     
    }

//------------------------------------------    
//  Show a message in an Alert box
	private void showMsg(String msg) {

		AlertDialog ad = new AlertDialog.Builder(this).create();
		ad.setCancelable(false); // This blocks the 'BACK' button
		ad.setMessage(msg);
		ad.setButton(DialogInterface.BUTTON_POSITIVE, "Clear message", 
		  new DialogInterface.OnClickListener() {
		    @Override
		    public void onClick(DialogInterface dialog, int which) {
		        dialog.dismiss();                    
		    }
		});
		ad.show();
	}
	
	// Utility method to pad time values to get HH:MM String
	private  String build_HHMM(int hr, int min) {
		StringBuilder sb = new StringBuilder();
		if (hr >= 10)
		   sb.append(String.valueOf(hr));
		else
		   sb.append("0").append(String.valueOf(hr));
		sb.append(":");
		if (min >= 10)
		   sb.append(String.valueOf(min));
		else
		   sb.append("0").append(String.valueOf(min));
		return sb.toString();
	}



}
