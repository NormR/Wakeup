package com.normsstuff.wakeup;

import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import com.normstools.SaveStdOutput;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class ScreenOnOffService extends Service {
	private static final int NOTIFY_ME_ID = 1377;
	
	private BroadcastReceiver mReceiver=null;
	private PowerManager.WakeLock wl_dim = null;
	private boolean haveSetTimer = false;  // used to set WU timer

    private SharedPreferences ourSP;
    private SharedPreferences.Editor ourSP_editor;
	
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HHmmss", Locale.US); // Builds Filename
    SimpleDateFormat sdfTime = new SimpleDateFormat("'T' HH:mm:ss", Locale.US); // show time
	String rootDir = Environment.getExternalStorageDirectory().getPath();
	final String LogFilePathPfx = rootDir + "/ScreenOnOff_log_";
	final String LogFilePathSfx = "_E.txt";
	
	private NotificationManager mgr = null;
	private boolean addedNotify = false;     // set true if startForeground() called
	private boolean turnOffWiFi = true;  // should be set by values passed in original Intent
	
    private PendingIntent alarmPI = null;
    private int buildLvl = -99;             // For new methods
	
	//  Times for alarms
	int startHour = 6;
	int startMinute = 30;
	int stopHour = 22;
	int stopMinute = 0;
	
	int postponeTime = 30;    // number of minutes to postpone setting alarm
	long CloseToAlarm = 20*1000;  // number of seconds that are close to an alarm time
	
	// Flags to go with setting of Sensor
	final String ChangeSensorS = "ChngSnsr";
	final String SetSensorOn = "SetOn";
	final String SetSensorOff = "SetOff";
	final String SensorStat = "SensorStat";
	
	final String AlarmTimeMillis = "AlrmMs";
	final String AlarmType = "AlrmType";   // Set to Start or Stop

	private boolean keepSensorOff = false;  // use to control setting of Sensor
	
	private int printOutFilterCnt = 1000;  // print every x times using %
	
	//===========================================================
	// Define classes etc for detecting when orientation changes
	enum Axis {X, Y, Z};  //  define indexes for values array

	abstract class TestBoundary {
		int valuesIndex;
        double bndryValue;
        double filter;

		public TestBoundary(Axis valIdx, double bndryVal, double filter) { 
         // Save the index for the array and the boundary value
		 valuesIndex = valIdx.ordinal();  //  convert to indexes: 0 thru 2
         bndryValue = bndryVal;
         this.filter = filter;
		}
		public abstract boolean pastBoundary(float[] v); //  pass current values
	}

	class TestIfAbove extends TestBoundary  {
	      TestIfAbove(Axis valIdx, double bndVal, double filter){
	         super(valIdx, bndVal, filter);
	      }
	      //  Test if values value is above boundary
	  	  public boolean pastBoundary(float[] values) {
//	         System.out.println("test above: tV="+testVal + " v=" +values[valuesIndex]);
				return bndryValue < values[valuesIndex];
	  	  }
	}

	class TestIfBelow extends TestBoundary  {
	      TestIfBelow(Axis valIdx, double bndVal, double filter){
	         super(valIdx, bndVal, filter);
	      }
	      //  Test if values value is below boundary
	  	  public boolean pastBoundary(float[] values) {
//	         System.out.println("test below: bV="+bndryValue + " v=" +values[valuesIndex]);
	  		  //  filter out values below filter value
	  		  if(values[valuesIndex] < filter)
	  			  return false;
	  		  return bndryValue > values[valuesIndex];
		  }
	}
	 
	private TestBoundary tstBndry;                // object for testing if orientation changed
	private boolean debugging = false;
	private boolean useWakeLock = false;
	
	//---------------------------------------------------
	//  For detecting orientation of device
    private SensorManager sensorManager = null;
    private boolean runningSensor = false;
    
	private int hitCount = 0;   // count number of Sensor on events before doing anything
	private int MinCount = 2;   // Number of events required before doing anything 
	// Note: there was problem with quick Sensor events when couch bounced.
  
    private final SensorEventListener sensorEventListener = new SensorEventListener() {
    	boolean haveShown = false;
    	int cnt = 0;           // use with % to reduce printouts
    	
    	long timeLastTurnOn = System.currentTimeMillis();  // use current time for first one
    	long TurnOnDelay = 5000; //  wait some time between trying to turn on screen again
    	
        public void onAccuracyChanged(Sensor sensor, int accuracy) { 
        	if(debugging)
        		System.out.println("onAccuracyChanged accuracy="+accuracy);
        }

        public void onSensorChanged(SensorEvent event) {
        	
          // Show some lines so we know Sensor is alive
          if(debugging && (cnt++ % printOutFilterCnt == 0) ) {
        	  System.out.println("Sensor values="+Arrays.toString(event.values) + " cnt="+cnt
        			  			 + " acc="+event.accuracy +", hitCount="+hitCount
        			             + " at " + sdfTime.format(new java.util.Date()));
          }
          
          // Check if orientation changed as desired (Horizontal to vertical)
          if(tstBndry.pastBoundary(event.values)) {
        	  hitCount++;              // count number of events
        	  
        	  if(!haveShown && (hitCount > MinCount)) {
        		  haveShown = true;    // only show once
        		  System.out.println("Sensor values="+Arrays.toString(event.values) 
        				  			 + ", since last=" + (System.currentTimeMillis() - timeLastTurnOn)
        				             +  ", cnt="+cnt + ", hitCount="+hitCount 
        				             + " at " + sdfTime.format(new java.util.Date()));
        		  if((System.currentTimeMillis() - timeLastTurnOn) > TurnOnDelay) {
        			  hitCount = 0; // Reset
        			  turnOnScreen();  // Go turn on the screen
        			  timeLastTurnOn = System.currentTimeMillis(); // save

        			  if(wl_dim != null && wl_dim.isHeld())  
        				  wl_dim.release();  // get rid of wl_dim
        		  }
        		  
        	  } else {
            	  if(debugging && !haveShown) 
            		  System.out.println("WU Sensor values=" + Arrays.toString(event.values) 
            				  			 +", haveShown=" + haveShown
            				  			 + ", hitCount=" + hitCount +  ", cnt="+cnt 
            				  			 + " at " + sdfTime.format(new java.util.Date()));
        	  }
        	  
          }else {
        	  // value not in range to turn on screen
        	  haveShown = false; // reset
        	  if(debugging && (hitCount > 0)) {
        		  System.out.println("WU Sensor values=" + Arrays.toString(event.values) 
        				  			 + ", hitCount=" + hitCount +  ", cnt="+cnt 
        				  			 + " at " + sdfTime.format(new java.util.Date()));
        	  }
        	  hitCount = 0;  // reset
          }
        	 
        }  // end onSensorChanged()
    };
    
    private Thread.UncaughtExceptionHandler lastUEH = null;  // For trapping exceptions

    //----------------------------------------------------
    // Define inner class to handle exceptions
    class MyExceptionHandler implements Thread.UncaughtExceptionHandler {
        public void uncaughtException(Thread t, Throwable e){
           java.util.Date dt =  new java.util.Date();
           String fn = rootDir + "/WakeUp_" + sdf.format(dt) + "_Trace.txt";   // 2014-02-02T193504
           try{ 
              PrintStream ps = new PrintStream( fn );
              e.printStackTrace(ps);
              ps.close();
              System.out.println("ScrnOnOffSrcv  wrote trace to " + fn);
              e.printStackTrace(); // capture here also???
              SaveStdOutput.stop(); // close here vs calling flush() in class  ???
           }catch(Exception x){
              x.printStackTrace();
           }
           lastUEH.uncaughtException(t, e); // call last one  Gives: "Unfortunately ... stopped" message
           return;    //???? what to do here
        }
     }


    private Handler handler; // Handler for the separate Thread

 	//=============================================================================
    @Override
    public void onCreate() {
        super.onCreate();
        
        Toast.makeText(getBaseContext(), "ScrnOnOffSrvc onCreate", Toast.LENGTH_SHORT).show();
        
        //  Here add code to detect/set what value to test for change in orientation
        //  detect if Z value is less than 4 with filter at 0
        tstBndry = new TestIfBelow(Axis.Z, 4.0, -2.0); 
        
        // Register receiver that handles screen on and screen off logic
        // Put the receiver on its own thread
        HandlerThread handlerThread = new HandlerThread("MyNewThread");
        handlerThread.start();
        // Now get the Looper from the HandlerThread so that we can create a Handler that is attached to
        //  the HandlerThread
        // NOTE: This call will block until the HandlerThread gets control and initializes its Looper
        Looper looper = handlerThread.getLooper();
        // Create a handler for the service
        handler = new Handler(looper);
        
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        mReceiver = new ScreenOnOffReceiver();
//        registerReceiver(mReceiver, filter);
        registerReceiver(mReceiver, filter, null, handler);
        
        // Get the user's preferences settings
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		debugging = preferences.getBoolean("set_debug_text", false);
		useWakeLock = preferences.getBoolean("use_wake_lock", false);
		
		String printOutFreq = preferences.getString("frequencyOfPO", "10000");
		printOutFilterCnt = Integer.parseInt(printOutFreq);
		String sensorCounts = preferences.getString("sensorCounts", "1");
		MinCount = Integer.parseInt(sensorCounts);
		
        java.util.Date dt =  new java.util.Date();

        if(debugging) {
	      	 // Quick and dirty debugging
	        String fn = LogFilePathPfx + sdf.format(dt) + LogFilePathSfx;   // 2014-02-02T193504_version 
	
	        try {
				SaveStdOutput.start(fn);  // Start trap for println()s
	   	    } catch (IOException e) {
	   			e.printStackTrace();
	   	    }
	        
	        // Set trap for exceptions
	         lastUEH = Thread.getDefaultUncaughtExceptionHandler(); // save previous one
	         Thread.setDefaultUncaughtExceptionHandler(new MyExceptionHandler());

        }  // end debugging stuff
        
        buildLvl = Build.VERSION.SDK_INT;  // need this for new AlarmManager method
        
        System.out.println("ScrnOnOffSrvc onCreate() created receiver at " + sdfTime.format(dt)
        		+ " >>printOut cnt="+printOutFilterCnt + " use_wake_lock="+useWakeLock
        		+ ", MinCount="+MinCount +", buildLvl="+buildLvl);
        
        // See if we have anything stored
    	ourSP = getSharedPreferences("ScreenOnOffSrvc", MODE_PRIVATE);
	    System.out.println(" >>> ourSP="+ ourSP.getAll());
	    
	    // Get the times from ourSP in case saved previously by onStartCommand()
	    startHour = ourSP.getInt(GetStopStartTimesActivity.Hour_S, startHour);
	    startMinute = ourSP.getInt(GetStopStartTimesActivity.Minute_S, startMinute);
	    stopHour = ourSP.getInt(GetStopStartTimesActivity.Hour_E, stopHour);
	    stopMinute = ourSP.getInt(GetStopStartTimesActivity.Minute_E, stopMinute);

       
        //  Following for debug - to see how long service runs
        //  print a message every so often
        if(debugging) {
	        Thread t1 = new Thread(new Runnable() {
	        	String msgId = sdfTime.format(new java.util.Date());
	        	
	        	public void run() {
	        		for(int i=0; i < 100; i++) {
	        			System.out.println("** run " + i +" " + msgId + "  @ " 
	        		                       + sdfTime.format(new java.util.Date()));
	        			try{Thread.sleep(60000);}catch(Exception x){};
	        		}
	        	}
	        });
//	        t1.start(); //<<<<<<<<<<<< COMMENT OUT FOR NOW ???
        }
    }  // end onCreate()

    private int callCnt = 0;  //  debug counts number of calls to following
    
    //--------------------------------------------------------------------------
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) { 
    	
    	boolean screenOff = false;
        java.util.Date dt =  new java.util.Date();
    	System.out.println("ScrnOnOffSrvc onStartCmd() start intent="+intent
    			            + ", startId="+ startId +", callCnt=" + ++callCnt +" at "
    			            + sdf.format(dt));
    	
    	// Get our personal SharedPreferences - used to keep track of Alarm state
    	ourSP = getSharedPreferences("ScreenOnOffSrvc", MODE_PRIVATE);
    	ourSP_editor = ourSP.edit();
    	
    	// Clear it out on the first call
    	if(startId == 1){
    		ourSP_editor.clear();
    		ourSP_editor.commit();
    	}else{
    	    System.out.println(" >>> ourSP="+ ourSP.getAll());
    	}
    	
    	/********************************************************************
    	 * Several flavors of Intents received here
    	 * 1) Intent = null when Service restarted after having been stopped ???
    	 * 2) First call from MainActivity on button press
    	 * 3) Called from OnAlarmReceive when an Alarm sent
    	 * 4) Called from ScreenOnOffReceiver when Screen state changed
    	*******************************************************************/
    	
    	// null if called by system if has been stopped
    	if(intent != null) 
    	{
    		Bundle bndl = intent.getExtras();
    		System.out.println(" >>data="+intent.getData()
         			+ "\n >>extras=" + (bndl == null ? "null" : Arrays.toString(bndl.keySet().toArray())));
    		
    		
    		//--------------------------------------------------------------------
    		// See if started by an Alarm
    		String sensorFlag = intent.getStringExtra(ChangeSensorS);
    		if(sensorFlag != null)
    		{
    			handleAlarmIntent(sensorFlag);
    		}  // end handling sensor flag from alarm
    		
    		else {  // There wasn't a sensor flag in the Intent
    			boolean firstStart = bndl.getBoolean(MainActivity.FirstStart, false);
    			//If no sensor flag then test if first call from MainActivity
    			if(firstStart) {
    	    		// Get the stop/start times
    	    		startHour = intent.getIntExtra(GetStopStartTimesActivity.Hour_S, startHour);
    	    		startMinute = intent.getIntExtra(GetStopStartTimesActivity.Minute_S, startMinute);
    	    		stopHour = intent.getIntExtra(GetStopStartTimesActivity.Hour_E, stopHour);
    	    		stopMinute = intent.getIntExtra(GetStopStartTimesActivity.Minute_E, stopMinute);
    	    		System.out.println("ScrnOnOffSrvc onStartCmd() stopTime="+stopHour + ":" + stopMinute
    	    							+",  startTime=" + startHour + ":" + startMinute);
    	    		
    	    		// Save in our SP
    	    		ourSP_editor.putInt(GetStopStartTimesActivity.Hour_S, startHour);
    	    		ourSP_editor.putInt(GetStopStartTimesActivity.Minute_S, startMinute);
    	    		ourSP_editor.putInt(GetStopStartTimesActivity.Hour_E, stopHour);
    	    		ourSP_editor.putInt(GetStopStartTimesActivity.Minute_E, stopMinute);
    	    		ourSP_editor.commit();
    				
	    			// Start the alarm for the Stop time
	    	        Calendar cal = Calendar.getInstance();
	    	        cal.set(Calendar.HOUR_OF_DAY, stopHour);
	    	        cal.set(Calendar.MINUTE, stopMinute);
        	        if(isBeforeNow(cal)){
        	        	cal.add(Calendar.DAY_OF_MONTH, 1);  // move to tomorrow
        	        }

	    	        // Don't set alarm if we are after Stop time
	    	        if(isAfterNow(cal)){
		       			System.out.println("ScrnOnOffSrvc setting Stop alarm for "
		       								+ sdf.format(cal.getTime()));
		       		    Bundle bndl1 = new Bundle();
		    	        bndl1.putString(ChangeSensorS, SetSensorOff);
		    	        setAlarm(cal.getTimeInMillis(), bndl1);
            	        ourSP_editor.putString(AlarmType, SetSensorOff);
	        	        ourSP_editor.putLong(AlarmTimeMillis, cal.getTimeInMillis());
	        	        ourSP_editor.commit();

	    	        }else{
	    	        	// Check if before Start time and ???
	    	        	System.out.println("ScrnOnOffSrvc - Skipped setting Stop alarm - past time");
	    	        }
    			}
    		}
    		
    		//- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    		// Get Screen ON/OFF values sent from receiver ( ScreenOnOffReceiver.java ) 
    		String state = intent.getStringExtra(ScreenOnOffReceiver.ScreenState);
    		if((state != null) && (!state.equals(ScreenOnOffReceiver.Unknown))) {
	            screenOff = state.equals(ScreenOnOffReceiver.ScreenOff);
	            
	        	//  Need logic here to turn on the Sensor if screen is off
	        	// and turn off the Sensor if screen is on
	            if(screenOff){
	            	// turn Sensor ON if screen is off
	            	turnOnSensor();
	 
	            	if(useWakeLock) {
	            		turnOnScreen_Dim();
	            		// Following as backup incase above doesn't work !!!
	            		if(!haveSetTimer) {
	//            			turnOnScreenAfterDelay(10*60*1000);  // For backup - turn on in 10 min
	            			haveSetTimer = true;   // only set one at a time
	            		}
	            	}
	            	
	            }else if(state.equals(ScreenOnOffReceiver.ScreenOn)) {
	            	// turn Sensor OFF is screen is on
	            	if(runningSensor) { // first time it will be off
	            		sensorManager.unregisterListener(sensorEventListener);
	        	        ourSP_editor.putString(SensorStat, SetSensorOff);   // save its status
	        	        ourSP_editor.commit();
	                	System.out.println("ScrnOnOffSrvc stopped Sensor at " + sdfTime.format(dt));
	            	}
		    		runningSensor = false;
		    		
	            } else{
	    			System.out.println("ScrnOnOffSrvc - unknown receiver state:"+state);
	    		}

    		}  // end got Screen change intent
    		
            // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 
            // Are we supposed to add a Notification?
            boolean addNotify = (boolean)intent.getBooleanExtra(MainActivity.AddNotify, false);
            if(addNotify){
				/*********** Create notification ***********/
				mgr = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
				
				// Build an intent with some data
				Intent intent4P = new Intent(getBaseContext(), MainActivity.class);
				// How to pass this to NotifyMessage -> Need flag on PI!!!!
				intent4P.putExtra("Notify at", sdfTime.format(new java.util.Date()));
				
				// This pending intent will open after notification click
				PendingIntent pi = PendingIntent.getActivity(getBaseContext(), 0, intent4P, 
						                                     PendingIntent.FLAG_UPDATE_CURRENT);
				
				Notification note = new Notification.Builder(getBaseContext())
									.setSmallIcon(R.drawable.ic_launcher_small)
									.setWhen(System.currentTimeMillis())
									.setTicker("Started Norm's WakeUp!")
									.setContentTitle("Norm's WakeUp is running")
									.setContentText("Touch to Open app for control @ "
											  + sdfTime.format(new java.util.Date()))
//									.setContentInfo("Norm's WakeUp")
									.setContentIntent(pi)
									.build();
				// See number of notification arrived
				note.number = 2;
//				mgr.notify(NOTIFY_ME_ID, note);
				startForeground(NOTIFY_ME_ID, note);
				addedNotify = true;                // remember for stop
				System.out.println("ScrnOnOffSrvc onStartCmd() started Notify at " 
						+ sdfTime.format(new java.util.Date()));
            }
            

    	}
    	else
    	{
    		//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    		//  If intent was null, were we restarted while screen was off?
    		// Is this a "missing" Alarm?
    		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    		if(!pm.isScreenOn()) {
    			// This method was deprecated in API level 20.
    			// Use isInteractive() instead.
    			
    	  		// If no intent but started by "fake" Alarm, ourSP has useful data
        		// Let's assume if started within x seconds of last alarm that
        		// the system has dropped the Alarm and started us anyway
     			// Check for fake alarm (ie null Intent within a time limit)
     	        Calendar cal = Calendar.getInstance();
     	        cal.set(Calendar.SECOND, 0);  //  So all have same value here
    	        long nowInMillis = cal.getTimeInMillis();  // current time
     	        // First get the stop time in ms
    	        cal.set(Calendar.HOUR_OF_DAY, stopHour);
    	        cal.set(Calendar.MINUTE, stopMinute);
    	        long stopInMillis = cal.getTimeInMillis();
    	        // Now get the start time in ms
    	        cal.set(Calendar.HOUR_OF_DAY, startHour);
    	        cal.set(Calendar.MINUTE, startMinute);
    	        long startInMillis = cal.getTimeInMillis();

    	        // Debug stuff???
    			long alarmTime = ourSP.getLong(AlarmTimeMillis, 0);
    			long distance = System.currentTimeMillis() - alarmTime;
				System.out.println("ScrnOnOffSrvc onStartCmd() fake alarm distance="+distance
						+ ", alarmTime="+sdfTime.format(alarmTime));
				
    			if((nowInMillis >= stopInMillis) 
    					&& (nowInMillis - stopInMillis) < CloseToAlarm) 
    			{
    				String alarmType = ourSP.getString(AlarmType, "???");
    				if(alarmType.equals(SetSensorOff)){
    					// Turn off Sensor and Wifi and set Alarm for Start
    			    	if(turnOffWiFi) {
    			    		WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE); 
    			    		wifiManager.setWifiEnabled(false);
    	                	System.out.println("ScrnOnOffSrvc - turned off WiFi");
    			    	}

    					setStartAlarm();
    					
    				}else if(alarmType.equals(SetSensorOn)) {
    					System.out.println("**** Should set Alarm OFF and turn on Sensor");
    					handleAlarmIntent(SetSensorOn); // ??? is this ok here
    					
    				}else{
    					System.out.println("???? Unknown Alarm Type " + alarmType);
    				}
    				
    				// see if after Start and before Stop
    			} else if((nowInMillis >= startInMillis) && (nowInMillis <= stopInMillis))	{
    				//  Turn on Sensor if Screen is off and we've been restarted(Intent=null)
	    			turnOnSensor(); 
   	            	if(useWakeLock) {
	            		turnOnScreen_Dim();
	            	}
   	            	
    			} else {
    	        	System.out.println("ScrnOnOffSrvc onStartCmd() time out of range at "
    						+ sdfTime.format(nowInMillis) + " stop@"+sdfTime.format(stopInMillis)
    						+ " start@"+sdfTime.format(startInMillis));
    	        	setStartAlarm();   // Set the alarm for starting up???
    			}
    			
    		}else{
    			System.out.println("??? Screen is on");
    			// Did we miss an alarm? 
    			long alarmTime = ourSP.getLong(AlarmTimeMillis, 0);
    			if(alarmTime < System.currentTimeMillis())
    				setStartAlarm(); //  Is this the right thing ???
    		}
    		
    	}  // end Intent was null
    	
      	System.out.println("ScrnOnOffSrvc onStartCmd() end screenOff="+screenOff 
      			        +", addedNotify="+addedNotify
      					+" at " + sdfTime.format(new java.util.Date()));

       // We want this service to continue running until it is explicitly
       // stopped, so return sticky.
	    return Service.START_STICKY;
    }  // end onStartCommand()
    
    //----------------------------------------------------------------------
    private void handleAlarmIntent(String sensorFlag) {
		System.out.println("ScrnOnOffSrvc sensorFlag="+sensorFlag);
		
		if(sensorFlag.equals(SetSensorOff)){
			keepSensorOff = true;
			
			//If screen is on and not charging, postpone this alarm for 30 minutes
    		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    		if(pm.isScreenOn()) {
    			// check if charging
    			IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    			Intent batteryStatus = registerReceiver(null, ifilter);

    			// Are we charging?
    		    int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
    			boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING;
    			if(isCharging){
    				System.out.println("ScrnOnOffSrvc Skipping postponing with Screen on and charging");
    				return;
    			}

    			
     	        Calendar cal = Calendar.getInstance();
    	        cal.add(Calendar.MINUTE, postponeTime);

       	        Bundle bndl1 = new Bundle();
    	        bndl1.putString(ChangeSensorS, SetSensorOff);
    	        setAlarm(cal.getTimeInMillis(), bndl1);
    	        ourSP_editor.putString(AlarmType, SetSensorOff);
    	        ourSP_editor.putLong(AlarmTimeMillis, cal.getTimeInMillis());
    	        ourSP_editor.commit();
   	        
    	        Toast.makeText(getBaseContext(), "Off alarm postponed for "+ postponeTime 
    	        				+" minutes.", Toast.LENGTH_LONG).show();
    	        System.out.println("ScrnOnOffSrvc Off alarm postponed for "+ postponeTime 
    	        				   +" minutes.");
    	        return; 		//   exit now
    		}
			
			// if Sensor is on, turn it off
			if(sensorManager != null) {
				sensorManager.unregisterListener(sensorEventListener);
		    	if(wl_dim != null && wl_dim.isHeld()) {
		    		wl_dim.release();
		    	}
		    	// Should we also turn off the WiFi while program rests?
		    	if(turnOffWiFi) {
		    		WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE); 
		    		wifiManager.setWifiEnabled(false);
                	System.out.println("ScrnOnOffSrvc - turned off WiFi");
		    	}

    	        ourSP_editor.putString(SensorStat, SetSensorOff);   // save its status
    	        ourSP_editor.commit();
            	System.out.println("ScrnOnOffSrvc - stopped Sensor at " 
            						+ sdfTime.format(new java.util.Date()));
			}else{
				System.out.println("ScrnOnOffSrvc sensorManager was null.");  // should never happen
			}
			// Set alarm for Start time
			setStartAlarm();
			
		}else if(sensorFlag.equals(SetSensorOn)){
			keepSensorOff = false;
			// This will normally be done early in the morning (eg 0630)
			// The Stop time will be this evening (eg 2200)
			// if screen off, turn sensor on
    		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    		if(!pm.isScreenOn()) {
    			// This method was deprecated in API level 20.
    			// Use isInteractive() instead.
    			turnOnSensor();
            	if(useWakeLock) {
            		turnOnScreen_Dim();
            	}
    		}
			
			// Start the alarm for the Stop time
	        Calendar cal = Calendar.getInstance();
	        cal.set(Calendar.HOUR_OF_DAY, stopHour);
	        cal.set(Calendar.MINUTE, stopMinute);
	        // Note: When testing cal could be in the past
	        if(isBeforeNow(cal)){
	        	cal.add(Calendar.DAY_OF_MONTH, 1);  // move to tomorrow
	        }
 	        if(isAfterNow(cal)){
	            // OK, cal is in the future
    			System.out.println("ScrnOnOffSrvc - setting Stop alarm for "
    	                            + sdf.format(cal.getTime()));
    	        Bundle bndl1 = new Bundle();
    	        bndl1.putString(ChangeSensorS, SetSensorOff);
    	        setAlarm(cal.getTimeInMillis(), bndl1);
    	        ourSP_editor.putString(AlarmType, SetSensorOff);
    	        ourSP_editor.putLong(AlarmTimeMillis, cal.getTimeInMillis());
    	        ourSP_editor.commit();

	        }else{
	        	System.out.println("ScrnOnOffSrvc skipping Stop time in the past "
	        						+ sdf.format(cal.getTime()));
    	        ourSP_editor.putLong(AlarmTimeMillis, 0);  // clear
    	        ourSP_editor.commit();
	        }
	        
		} else{  // Should never happen
			System.out.println("ScrnOnOffSrvc unknown sensorFlag " + sensorFlag);
		}

    }  // end handleAlarmIntent()
    
    //---------------------------------------------------------------
	// This will normally be done in the evening (eg 2200)
	// The Start time will be tomorrow morning (eg 0630)
    private void setStartAlarm() {
	    Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, startHour);
        cal.set(Calendar.MINUTE, startMinute);
        Calendar now = Calendar.getInstance();
        if(now.compareTo(cal) > 0){
        	cal.add(Calendar.DAY_OF_MONTH, 1); // move to tomorrow
        }
   		System.out.println("ScrnOnOffSrvc setting Start alarm for "
                            + sdf.format(cal.getTime()));
   		Bundle bndl1 = new Bundle();
        bndl1.putString(ChangeSensorS, SetSensorOn);
        setAlarm(cal.getTimeInMillis(), bndl1);
        ourSP_editor.putString(AlarmType, SetSensorOn);
        ourSP_editor.putLong(AlarmTimeMillis, cal.getTimeInMillis());
        ourSP_editor.commit();	
    }
    
    //----------------------------------------------------
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
	
	//------------------------------------------------------------
	// Check if date/time in cal is before the current time
	private boolean isBeforeNow(Calendar cal){
        Calendar now = Calendar.getInstance();
        return (now.compareTo(cal) > 0);
	}
	private boolean isAfterNow(Calendar cal){
        Calendar now = Calendar.getInstance();
        return (now.compareTo(cal) < 0);
	}
	private boolean isNowBetweenTimes(){
		// Define between as after Start and before Stop
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, startHour);
        cal.set(Calendar.MINUTE, startMinute);
        if(isBeforeNow(cal))
        	return false;    // before the Start time EG 0600
        cal.set(Calendar.HOUR_OF_DAY, stopHour);
        cal.set(Calendar.MINUTE, stopMinute);
        if(isAfterNow(cal))
        	return false;  // after the Stop time EG 2200
		return true;
	}
	
	//-----------------------------------------------
    private void turnOnSensor() {
	    sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		
	    Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
	    sensorManager.registerListener(sensorEventListener,
	                                   accelerometer,
	                                   SensorManager.SENSOR_DELAY_NORMAL);  // slowest ???
	    runningSensor = true;
    	System.out.println("ScrnOnOffSrvc started Sensor  at " + sdfTime.format(new java.util.Date()));
        ourSP_editor.putString(SensorStat, SetSensorOn);   // save its status
        ourSP_editor.commit();
    }
    
 	
    //-------------------------------------------------------------------------------------
    private void turnOnScreen() {
    	// Get rid of DIM WakeLock
    	if(wl_dim != null && wl_dim.isHeld()) {
    		wl_dim.release();
    	}

	     //  Turn on the screen 
	     PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
//	     PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
	     final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK 
	    		                                  		 | PowerManager.ACQUIRE_CAUSES_WAKEUP, 
	    		                                  		"Screen On");
	     wl.acquire();
	     
	     System.out.println("ScrnOnOffSrvc Turned on screen at "+ sdfTime.format(new java.util.Date())
	    		  			+ " ScreenOn="+pm.isScreenOn());
	     
	     Thread t2 = new Thread(new Runnable() {
	    	 public void run() {
	    		 try{Thread.sleep(1000);}catch(Exception x){}  // wait some ???
	    		 wl.release(); // get rid of it and hope screen stays on a while???
	    	 }
	     });
	     t2.start();
    }
    
    //--------------------------------------------------------------------------
    // Called to keep accelerometer sensor alive when screen is off
    private void turnOnScreen_Dim() {
	     PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
//	     wl_dim = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Dim Lock");
	     // Ensures that the CPU is running; the screen and keyboard backlight will be allowed to go off.
	     wl_dim = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Partial Lock");
	     wl_dim.acquire();
	     System.out.println("ScrnOnOffSrvc turned on PARTIAL_WAKE_LOCK at " 
	    		 			+ sdfTime.format(new java.util.Date()));
/*	   NOTE: getWindow() not defined here  
	     // Try this also to see if it helps keep Sensor sending data
	     WindowManager.LayoutParams params = getWindow().getAttributes();
	     params.flags |= LayoutParams.FLAG_KEEP_SCREEN_ON;
	     params.screenBrightness = 0.01f;
	     getWindow().setAttributes(params);
*/
    }
    
    private void turnOnScreenAfterDelay(int delay) {
        Runnable runnable = new Runnable() {
            public void run() {
            	System.out.println("ScrnOnOffSrvc runnable turning on screen  at " 
            					   + sdfTime.format(new java.util.Date()));
                turnOnScreen();
                haveSetTimer = false;  // reset once this one has been done
            }
        };
        new Handler().postDelayed(runnable, delay);
        System.out.println("ScrnOnOffSrvc set to turn on after delay="+delay +" at " 
        					+ sdfTime.format(new java.util.Date()));
    }
    
    //----------------------------------------------------------------------
    // Set an alarm to call us seconds from now
    @TargetApi(Build.VERSION_CODES.M)    // allow M code
    private void setAlarm(long theTime,  Bundle bndl){
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(getBaseContext(), OnAlarmReceive.class);
        intent.putExtras(bndl);        // pass the bundle to the next one
        alarmPI = PendingIntent.getBroadcast(
                       this, 0, intent,
                       PendingIntent.FLAG_UPDATE_CURRENT);
        
        if(buildLvl >= Build.VERSION_CODES.M) {
         	  alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, theTime, alarmPI);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, theTime, alarmPI);
        }
        System.out.println("ScrnOnOffSrvc Alarm set for " + sdf.format(theTime));
    }
    
    //-----------------------------------------------------------------------------------------------
	@Override
	public void onDestroy() {
	    System.out.println("ScrnOnOffSrvc onDestroy() at " + sdfTime.format(new java.util.Date()));
	    System.out.println(">>>ourSP="+ (ourSP != null ? ourSP.getAll() : "none"));
	    
	    if(addedNotify)
	    	stopForeground(true);
	    
	    if(alarmPI != null) {
	        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
	        alarmManager.cancel(alarmPI);
	        // Clear our Alarm SharedPreferences
	        if(ourSP_editor != null) {
		        ourSP_editor.putString(AlarmType, "Cleared");
		        ourSP_editor.putLong(AlarmTimeMillis, 0);
		        ourSP_editor.commit();	
	        }
	    }

	    try {
		    if(mReceiver!=null) {
		       unregisterReceiver(mReceiver);
			}
	    	if(runningSensor) { 
	    		sensorManager.unregisterListener(sensorEventListener);
	    	}
	    	if(mgr != null) {
	    		mgr.cancel(NOTIFY_ME_ID);   // what id ???
	    	}
	    	if(wl_dim != null && wl_dim.isHeld())
	    		wl_dim.release();
	    	
		}catch(Exception x) {
			x.printStackTrace();
		}		
		
		SaveStdOutput.stop();	// Stop writing to the debug log
	}

}