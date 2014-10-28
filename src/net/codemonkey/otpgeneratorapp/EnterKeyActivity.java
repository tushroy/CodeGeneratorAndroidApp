package net.codemonkey.otpgeneratorapp;

import java.io.IOException;
import net.tushar.util.otp.Base32String;
import net.tushar.util.otp.Base32String.DecodingException;
import net.tushar.util.otp.NetworkTimeProvider;
import net.tushar.util.otp.OtpType;
import org.apache.http.impl.client.DefaultHttpClient;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class EnterKeyActivity extends Activity implements TextWatcher {
	private static final int MIN_KEY_BYTES = 10;
	private EditText mKeyEntryField;
	private Spinner mType;

	private SharedPreferences mPrefs;

	/**
	 * Called when the activity is first created
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// setPageContentView(R.layout.enter_key);
		setContentView(R.layout.enter_key);

		// getting preferences
		mPrefs = this.getSharedPreferences(this.getPackageName(),
				Context.MODE_PRIVATE);

		// Find all the views on the page
		mKeyEntryField = (EditText) findViewById(R.id.key_value);
		mKeyEntryField.setText(mPrefs.getString("secret", ""));
		mType = (Spinner) findViewById(R.id.type_choice);

		ArrayAdapter<CharSequence> types = ArrayAdapter.createFromResource(
				this, R.array.type, android.R.layout.simple_spinner_item);
		types.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mType.setAdapter(types);

		// Set listeners
		mKeyEntryField.addTextChangedListener(this);

		mType.setSelection(mPrefs.getInt("type", 0));
	}

	/*
	 * Return key entered by user, replacing visually similar characters 1 and
	 * 0.
	 */
	private String getEnteredKey() {
		String enteredKey = mKeyEntryField.getText().toString();
		return enteredKey.replace('1', 'I').replace('0', 'O');
	}

	/*
	 * Verify that the input field contains a valid base32 string, and meets
	 * minimum key requirements.
	 */
	private boolean validateKeyAndUpdateStatus(boolean submitting) {
		String userEnteredKey = getEnteredKey();
		// checking if empty or null
		if (userEnteredKey.equals("") || userEnteredKey == null) {
			mKeyEntryField.setError(getString(R.string.error_empty_secret));
			return false;
		}
		// if not null or empty checks rest
		else {
			try {
				byte[] decoded = Base32String.decode(userEnteredKey);
				if (decoded.length < MIN_KEY_BYTES) {
					// If the user is trying to submit a key that's too short,
					// then display a message saying it's too short.
					mKeyEntryField
							.setError(submitting ? getString(R.string.enter_key_too_short)
									: null);
					return false;
				} else {
					mKeyEntryField.setError(null);
					return true;
				}
			} catch (DecodingException e) {
				mKeyEntryField
						.setError(getString(R.string.enter_key_illegal_char));
				return false;
			}

		}
	}

	protected void validateEverythingAndSave(View view) {

		if (validateKeyAndUpdateStatus(true)) {
			// Save things here
			Editor editor = mPrefs.edit();
			editor.putString("secret", mKeyEntryField.getText().toString());
			editor.putInt("type", mType.getSelectedItemPosition());
			editor.commit();
			Toast.makeText(this, R.string.secret_saved, Toast.LENGTH_SHORT)
					.show();
			setResult(RESULT_OK);
			finish();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void afterTextChanged(Editable userEnteredValue) {
		validateKeyAndUpdateStatus(false);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
			int arg3) {
		// Do nothing
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
		// Do nothing
	}

	public void onSaveClick(View view) throws IOException {
		validateEverythingAndSave(view);

		new AsyncTask<Void, Void, Void>() {

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
		}.execute();
	}

	public void onScanQR(View view) {
		final int SCAN_REQUEST = 31337;
		Intent intentScan = new Intent("com.google.zxing.client.android.SCAN");
		intentScan.putExtra("SCAN_MODE", "QR_CODE_MODE");
		intentScan.putExtra("SAVE_HISTORY", false);
		try {
			startActivityForResult(intentScan, SCAN_REQUEST);
		} catch (ActivityNotFoundException error) {
			Dialog dialog = null;
			AlertDialog.Builder dlBuilder = new AlertDialog.Builder(this);
			dlBuilder.setTitle("Install Barcode Scanner?");
			dlBuilder
					.setMessage("To scan QR codes containing keys, you must install ZXing barcode scanner");
			dlBuilder.setIcon(android.R.drawable.ic_dialog_alert);
			dlBuilder.setPositiveButton("Install",
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog,
								int whichButton) {
							Intent intent = new Intent(
									Intent.ACTION_VIEW,
									Uri.parse("market://search?q=pname:com.google.zxing.client.android"));
							try {
								startActivity(intent);
							} catch (ActivityNotFoundException e) {
								//if no market app
								intent = new Intent(
										Intent.ACTION_VIEW,
										Uri.parse("https://zxing.googlecode.com/files/BarcodeScanner3.1.apk"));
								startActivity(intent);
							}
						}
					});
			dlBuilder.setNegativeButton("Cancel", null);
			dialog = dlBuilder.create();
			dialog.show();
		}

	}

	@SuppressLint("DefaultLocale")
	private void interpretScanResult(Uri scanResult) {
		final String SECRET_PARAM = "secret";
		final String OTP_SCHEME = "otpauth";
		final String scheme = scanResult.getScheme().toLowerCase();
		final String authority = scanResult.getAuthority();
		if (OTP_SCHEME.equals(scheme)) {
			String secret = scanResult.getQueryParameter(SECRET_PARAM);
			Log.i("Got Secret", secret);
			mKeyEntryField.setText(secret);
			if (OtpType.HOTP.name().toLowerCase().equals(authority)) {
				mType.setSelection(1);
			} else {
				mType.setSelection(0);
			}
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		// super.onActivityResult(requestCode, resultCode, data);
		final int SCAN_REQUEST = 31337;
		if (requestCode == SCAN_REQUEST && resultCode == Activity.RESULT_OK) {
			// Grab the scan results and convert it into a URI
			String scanResult = (intent != null) ? intent
					.getStringExtra("SCAN_RESULT") : null;
			Uri uri = (scanResult != null) ? Uri.parse(scanResult) : null;
			interpretScanResult(uri);
		}
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		finish();
	}
}
