package net.codemonkey.otpgeneratorapp;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.impl.client.DefaultHttpClient;

import net.tushar.util.otp.NetworkTimeProvider;
import net.tushar.util.otp.OtpProvider;
import net.tushar.util.otp.OtpSourceException;
import net.tushar.util.otp.OtpType;
import net.tushar.util.otp.TotpClock;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	private SharedPreferences mPrefs;
	private String mSecret;
	private OtpType mOtpType;
	private int mTimeCorrection;
	
	private TotpClock mTotpclock;
	private OtpProvider mOtpProvider;
	private long approxDecrementRate;
	private long codeChangeIntervalSecs;

	private TextSwitcher tSwitcherPin;
	private ProgressBar mProgressBar;
	private Timer mTimer;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// getting preferences
		mPrefs = this.getSharedPreferences(this.getPackageName(),
				Context.MODE_PRIVATE);
		mSecret = mPrefs.getString("secret", null);
		mTimeCorrection = mPrefs.getInt("timecorrection", 0);
		mOtpType = OtpType.getEnum(mPrefs.getInt("type", 0));

		if (mSecret.contentEquals("") || mSecret == null) {
			startEnterKeyActivity();
			setContentView(R.layout.key_not_set);
		} else {
			setContentView(R.layout.activity_main);

			// Text Switcher Animation
			tSwitcherPin = (TextSwitcher) findViewById(R.id.tsPincode); // init
																		// TextSwitcher
			tSwitcherPin.setInAnimation(this, android.R.anim.slide_in_left);
			tSwitcherPin.setOutAnimation(this, android.R.anim.slide_out_right);
			TextView tv1 = new TextView(this);
			TextView tv2 = new TextView(this);
			tv1.setTextSize(70);
			tv2.setTextSize(70);
			tSwitcherPin.addView(tv1);
			tSwitcherPin.addView(tv2);
			// Text Switcher Animation

			mTotpclock = new TotpClock(); //init clock
			mTotpclock.setTimeCorrectionMinutes(mTimeCorrection); //set time correction
			mOtpProvider = new OtpProvider(mTotpclock); // init provider

			//code change interval in secs
			codeChangeIntervalSecs = mOtpProvider.getTotpCounter().getTimeStep();
	
			// get approx decrement rate of progress
			approxDecrementRate = (100L / codeChangeIntervalSecs);

			// Concentrate on this line
			tSwitcherPin.setText(getPinCode(mSecret, mOtpType, 1));

			mProgressBar = (ProgressBar) findViewById(R.id.time_remaining_progressBar);
			
			//initial progress is based on time remaining
			mProgressBar.setProgress(Math.round(getClockProgressPercent()));

			mTimer = new Timer();

			// calculate amount of delay on first execution of timer
			long firstTick = getClockProgressPercent() * 10L;

			mTimer.scheduleAtFixedRate(new TimerTask() {
				
				//calculate remaining time to change code
				long clockProgress=getClockProgressPercent()-approxDecrementRate;
				
				@Override
				public void run() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							if ( clockProgress< approxDecrementRate) {
								clockProgress = 100L;
								mProgressBar.setProgress(100);
								tSwitcherPin.setText(getPinCode(mSecret,mOtpType, 1));
							} else {
								//calculate remaining time to change code
								clockProgress = (getClockProgressPercent()-approxDecrementRate);
								mProgressBar.setProgress(Math.round(clockProgress));
							}
						}
					});

				}
			}, firstTick, 1000L);
		}
	}
	
	private long getClockProgressPercent() {
		//return clock progress percentage in long
		return ((100L * mOtpProvider.getSecondsTillNextCounterValue()) / codeChangeIntervalSecs);

	}
	
	private String getPinCode(String secret, OtpType type, int counter) {
		try {
			return mOtpProvider.getNextCode(secret, type, counter);
		} catch (OtpSourceException e) {
			e.printStackTrace();
			return null;
		}
	}
	public void enterEditKey(View view) {
		startEnterKeyActivity();
	}

	private void startEnterKeyActivity() {
		Intent intent = new Intent(this, EnterKeyActivity.class);
		startActivityForResult(intent, 8874);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK && requestCode == 8874) {
			Intent refresh = new Intent(this, MainActivity.class);
			startActivity(refresh);
			this.finish();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case R.id.add_edit_key_menu:
			startEnterKeyActivity();
			return true;

		case R.id.about_menu:
			Builder aboutDialog = new AlertDialog.Builder(MainActivity.this);
			aboutDialog.setTitle(R.string.app_name);
			aboutDialog.setMessage(R.string.about_msg);
			aboutDialog.setCancelable(true);
			aboutDialog.setNeutralButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
						}
					});
			aboutDialog.show();
			return true;

		case R.id.sync_time_menu:
			// Syncing time correction
			new AsyncTask<Void, Void, Void>() {
				private ProgressDialog dialog;

				@Override
				protected void onPreExecute() {
					super.onPreExecute();
					dialog = new ProgressDialog(MainActivity.this);
					dialog.setMessage("Syncing Time Server");
					dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
					dialog.setCancelable(false);
					dialog.show();
				}

				@Override
				protected Void doInBackground(Void... params) {
					NetworkTimeProvider ntp = new NetworkTimeProvider(
							new DefaultHttpClient());
					long time = 0;
					int correction = 0;
					try {
						time = ntp.getNetworkTime();
						correction = ntp.getTimeCorrectionMinutes();
					} catch (IOException e) {
						e.printStackTrace();
					}
					Editor editor = mPrefs.edit();
					editor.putInt("timecorrection", correction);
					editor.commit();
					Log.i("Time", correction + " " + Long.toString(time));
					return null;
				}

				@Override
				protected void onPostExecute(Void result) {
					super.onPostExecute(result);
					dialog.dismiss();
				}

			}.execute();
			return true;

		case R.id.delete_secret:
			Editor editor = mPrefs.edit();
			editor.putString("secret", "");
			editor.putInt("type", 0);
			editor.commit();
			Toast.makeText(this, "Key Deleted", Toast.LENGTH_SHORT).show();
			setContentView(R.layout.key_not_set);
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

}
