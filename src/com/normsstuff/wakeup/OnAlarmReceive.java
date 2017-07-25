package com.normsstuff.wakeup;

import java.text.SimpleDateFormat;
import java.util.Locale;

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.os.Bundle;

public class OnAlarmReceive extends BroadcastReceiver {
	
	final public static String Sender = "Sender";
	
	   SimpleDateFormat sdfTime = new SimpleDateFormat("yyyy-MM-dd 'T' HH:mm:ss", Locale.US); // showTime

	
	  @Override
	  public void onReceive(Context context, Intent intent) {
	 
	     System.out.println("OnAlarmReceive, in onReceive. intent="+intent 
	    		 			+ "\n >>>"+sdfTime.format(new java.util.Date()));
	     Bundle bndl = intent.getExtras();
	     
	     String sender = bndl.getString(Sender);
	     if(sender != null) {
	    	 if(sender.equals("MainActivity")) {
			     // Start the MainActivity
			     Intent i = new Intent(context, MainActivity.class);
			     i.putExtra("StartedBy", "OnAlarmReceive");
			     i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			     context.startActivity(i);
			     System.out.println("onReceive started MainActivity");
			     return;     // exit - done
	    	 }
	     }
	     // Default will be to start the ScreenOnOffService
	     Intent int2 = new Intent(context, ScreenOnOffService.class);
	     int2.putExtras(bndl); // pass what we got
	     int2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);   //????
	     context.startService(int2);
	     System.out.println("onReceive started ScreenOnOffService at "
	                         +sdfTime.format(new java.util.Date()));
	  }

}
