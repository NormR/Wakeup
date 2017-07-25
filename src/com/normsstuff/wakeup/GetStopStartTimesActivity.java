package com.normsstuff.wakeup;

import java.util.Calendar;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.view.View.OnClickListener;

import android.widget.Button;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

public class GetStopStartTimesActivity extends Activity 
{
	// Define fields and classes for the Start and Stop(aka End) times
	final public static String Minute_S = "minuteS";  // for start times
	final public static String Hour_S = "hourS";
	final public static String Minute_E = "minuteE";  // for end times
	final public static String Hour_E = "hourE";
	
	private TextView textViewTimeS;
	private TextView textViewTimeE;
	private Button button;
 

	//======================================================================
	public static class TimePickerFragment extends DialogFragment
    			                           implements TimePickerDialog.OnTimeSetListener {
		
		int	hour;
		int minute;

		@Override
		public Dialog onCreateDialog(Bundle savedIS) {
		// Use the current time as the default values for the picker
//			final Calendar c = Calendar.getInstance();
//			int hour = c.get(Calendar.HOUR_OF_DAY);
//			int minute = c.get(Calendar.MINUTE);
			
			// Get the time from the Bundle
			Bundle bndl = getArguments();
			if(bndl != null) {
				minute = bndl.getInt(Minute_S);
				hour = bndl.getInt(Hour_S);
				System.out.println("TPF onCreateDialog hour="+hour+", minute="+minute);
			}else{
				System.out.println("TPF onCreateDialog bndl = null");
			}
			
			// Create a new instance of TimePickerDialog and return it
			return new TimePickerDialog(getActivity(), this, hour, minute,
							DateFormat.is24HourFormat(getActivity()));
		}
		
		public void onTimeSet(TimePicker view, int chosenHour, int chosenMinute) {
			hour = chosenHour;  //  save chosen time
			minute = chosenMinute;
			// Pass the time back to ...
			((GetStopStartTimesActivity)getActivity()).setTimeInView(hour, minute);
			System.out.println("TPF oTS  hour="+chosenHour + ", minute="+minute);
		}
	}  // end class TimePickerFragment

	//-------------------------------------------------------------------------
	// Define class to hold hour and minute values and build a display String
	class HHMM {
		int hour;
		int minute;
		
		public String getHHMM() {
			return new StringBuilder().append(padding_str(hour)).append(":")
                    .append(padding_str(minute)).toString();
		}
		private  String padding_str(int c) {
			if (c >= 10)
			   return String.valueOf(c);
			else
			   return "0" + String.valueOf(c);
		}
	}  // end class HHMM
	
	HHMM startTime = new HHMM();
	HHMM stopTime = new HHMM();
	HHMM currentHHMM = null;    // set for when a change button is pressed
	TextView currentView = null;
	
	//-------------------------------------------------------------------------
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.get_stop_start_times);
		
		// Display the current time 
		textViewTimeS = (TextView) findViewById(R.id.startTime);
		textViewTimeE = (TextView) findViewById(R.id.endTime);
		 
		// Use current times?
		final Calendar c = Calendar.getInstance();
		int hour = c.get(Calendar.HOUR_OF_DAY);
		int minute = c.get(Calendar.MINUTE);
		
		// Get the values passed in the Intent
		Intent startIntent = getIntent();
		startTime.hour = startIntent.getIntExtra(Hour_S, hour);
		startTime.minute = startIntent.getIntExtra(Minute_S, minute);
		stopTime.hour = startIntent.getIntExtra(Hour_E, hour);
		stopTime.minute = startIntent.getIntExtra(Minute_E, minute);
 
		// set current time into textview
		textViewTimeS.setText(startTime.getHHMM());
		textViewTimeE.setText(stopTime.getHHMM());
		
		// Now setup the buttons
		button = (Button) findViewById(R.id.changeStartBtn);
		button.setOnClickListener(new OnClickListener() {
 			@Override
			public void onClick(View v) {
 			    DialogFragment newFragment = new TimePickerFragment();
 			    Bundle args = new Bundle();
 			    args.putInt(Minute_S, startTime.minute);
 			    args.putInt(Hour_S, startTime.hour);
 			    currentHHMM = startTime;  // set ptr to data to be updated
 			    currentView = textViewTimeS;
 			    newFragment.setArguments(args);
 			    newFragment.show(getFragmentManager(), "timePicker");
 			    System.out.println("BL onClick after show()");
			}
		});
		button = (Button) findViewById(R.id.changeEndBtn);
		button.setOnClickListener(new OnClickListener() {
 			@Override
			public void onClick(View v) {
 			    DialogFragment newFragment = new TimePickerFragment();
 			    Bundle args = new Bundle();
 			    args.putInt(Minute_S, stopTime.minute);
 			    args.putInt(Hour_S, stopTime.hour);
 			    currentHHMM = stopTime;  // set ptr to data to be updated
 			    currentView = textViewTimeE;
 			    newFragment.setArguments(args);
 			    newFragment.show(getFragmentManager(), "timePicker");
 			    System.out.println("BL onClick after show()");
			}
		});
		
		button = (Button) findViewById(R.id.done_button);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v){
/*				
				// Testing only - Check that Start time is before Stop time  ???
				if(startTime.hour < stopTime.hour) {
			        Toast.makeText(getBaseContext(), "Error: Stop time must be before Start time", 
			        		Toast.LENGTH_LONG).show();
					return; // Don't allow it
				}
*/
				// Return the times in an Intent
				Intent intDone = new Intent();
				intDone.setAction("Done");
				intDone.putExtra(Hour_S, startTime.hour);
				intDone.putExtra(Minute_S,  startTime.minute);
				intDone.putExtra(Hour_E, stopTime.hour);
				intDone.putExtra(Minute_E,  stopTime.minute);
		
		        setResult(RESULT_OK, intDone);
				finish();
				System.out.println("BL onClick after finish()");
			}
		});
		//  SHould there be a cancel button????
	}
	
	
	//---------------------------------------------------------------
	// Called from the Fragment class to save the times and show them
	public void setTimeInView(int hour, int minute) {
		currentHHMM.hour = hour;
		currentHHMM.minute = minute;
		currentView.setText(currentHHMM.getHHMM());
		System.out.println("GSET sTIV hour="+hour + ", minute="+minute);
	}
	
}
