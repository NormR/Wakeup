package com.normsstuff.wakeup;


import java.text.SimpleDateFormat;
import java.util.Locale;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ScreenOnOffReceiver extends BroadcastReceiver {
    private boolean screenOff = false;  // Default value ????
    private String state = "??";
    
    // Define Strings to pass back in Intent
    final public static String ScreenState = "ScreenState";
    final public static String ScreenOn = "ScrnOn";
    final public static String ScreenOff = "ScrnOff";
    final public static String Unknown = "Unknown";
    
    
    SimpleDateFormat sdf = new SimpleDateFormat("'T' HH:mm:ss", Locale.US);  // for time

    @Override
    public void onReceive(Context context, Intent intent) {
    	
    	//Toast.makeText(context, "BroadcastReceiver", Toast.LENGTH_SHORT).show();
       System.out.println("BCRcvr onReceive() intent="+intent); 
    	
        if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
        	
            screenOff = true;
            state = ScreenOff;
            
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
        	
            screenOff = false;
            state = ScreenOn;
            
        } else{
        	// Catch not one of the above
        	System.out.println("**BCRcvr onReceive() - Unknown action:"+intent.getAction());
        	state = Unknown;
        }
        
        //Toast.makeText(context, "BroadcastReceiver :"+screenOff, Toast.LENGTH_SHORT).show();
        
        // Send Current screen ON/OFF value to service
        Intent i = new Intent(context, ScreenOnOffService.class);
        i.putExtra(ScreenState, state);
        context.startService(i);
        
        java.util.Date dt =  new java.util.Date();
        System.out.println("BCRcvr onReceive() started service // screenOff=" + screenOff
        					+ ", intent=" + intent
        		            + " at " + sdf.format(dt));
    }

}