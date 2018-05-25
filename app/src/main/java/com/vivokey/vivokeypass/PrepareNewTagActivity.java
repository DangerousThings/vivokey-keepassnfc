/*
 * This is free and unencumbered software released into the public domain.
 * 
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 * 
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 * 
 * For more information, please refer to [http://unlicense.org]
 */

package com.vivokey.vivokeypass;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.vivokey.vivokeypass.keepassapp.KeePassApp;
import com.vivokey.vivokeypass.keepassapp.KeePassApps;

import static android.view.View.VISIBLE;

public class PrepareNewTagActivity extends Activity {
	private class StringWithId {
		private String value, id;

		StringWithId(String value, String id) {this.value = value; this.id = id;}

		@Override
		public String toString() { return value; }
	}

	private static final int REQUEST_KEYFILE = 0;
	private static final int REQUEST_DATABASE = 1;
    private static final int REQUEST_NFC_WRITE = 2;
    private static final int REQUEST_PIN_CONFIGURATION = 3;
	private static final Uri whatIsKPNFCUrl = Uri.parse("http://vivokey.co/vivokeypass");

	private List<StringWithId> availableAppNames = new ArrayList<>();
	private DatabaseInfo dbinfo = null;

	@Override
	protected void onCreate(Bundle sis) {
		super.onCreate(sis);
		setContentView(R.layout.activity_configure);
	}

	@Override
	protected void onResume() {
		super.onResume();

		/* Reload DB info and populate view. */
		dbinfo = DatabaseInfo.deserialise(this);

		/* Populate the list of available apps */
		availableAppNames.clear();
		for(KeePassApp app: KeePassApps.get().getAvailableApps(getPackageManager())) {
			availableAppNames.add(new StringWithId("Launch " + app.getName(), app.getId()));
		}

		if(availableAppNames.isEmpty()) {
			Intent intent = new Intent();
			intent.setClass(this, NoKeepassActivity.class);
			startActivity(intent);
			finish();
		} else {
			initialiseView();
		}

		setWriteNfcButtonEnabled();
		NfcReadActions.nfc_enable(this);
	}

	@Override
	protected void onPause() {
		super.onPause();

		NfcReadActions.nfc_disable(this);
	}


	@Override
	protected void onSaveInstanceState(Bundle sis)
	{
		// We save state immediately after modification, so nothing to do here.
	    super.onSaveInstanceState(sis);
	}

	private void openPicker(int result) {
		// NB GET_CONTENT not guaranteed to return persistable URIs (E.g. Drive does not), hence OPEN_DOCUMENT here
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
		intent.setType("*/*");
		//intent.setData(Uri.parse(Environment.getExternalStorageDirectory().getPath()));

		startActivityForResult(intent, result);
	}

	private void initialiseView()
	{
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

		Uri keyfile = dbinfo.keyfile;
		findViewById(R.id.b_noKeyfile).setVisibility(keyfile == null ? View.INVISIBLE : VISIBLE);
		if(keyfile == null) {
			((TextView) (findViewById(R.id.keyfile_name))).setText(R.string.no_keyfile_selected);
		} else {
			((TextView) (findViewById(R.id.keyfile_name))).setText(getUriFilename(keyfile));
		}

		Uri database = dbinfo.database;
		if(database == null) {
			((TextView) (findViewById(R.id.database_name))).setText(R.string.no_db_selected);
		} else {
			((TextView) (findViewById(R.id.database_name))).setText(getUriFilename(database));
		}

		Button b = (Button) findViewById(R.id.write_nfc);
		b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View self) {
				self.setEnabled(false);
				switchToWriteNfcActivity();
			}
		});
		setWriteNfcButtonEnabled();

		findViewById(R.id.rl_database).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				openPicker(REQUEST_DATABASE);
			}
		});

		findViewById(R.id.rl_keyfile).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				openPicker(REQUEST_KEYFILE);
			}
		});

		findViewById(R.id.b_noKeyfile).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				((TextView)findViewById(R.id.keyfile_name)).setText(R.string.no_keyfile_selected);
				dbinfo.keyfile = null;
				storeDbInfo();
				view.setVisibility(View.INVISIBLE);
			}
		});

		findViewById(R.id.rl_pin).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				onPinClick();
			}
		});

		findViewById(R.id.ib_questionmark).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent intent = new Intent(Intent.ACTION_VIEW, whatIsKPNFCUrl);
				startActivity(intent);
			}
		});

		TextView tv = (TextView)findViewById(R.id.pin_status);
		if(dbinfo.pin != null && dbinfo.pin.length() > 0) {
			tv.setText(R.string.pin_configured);
		} else {
			tv.setText(R.string.no_pin_entered);
		}

		// Only allow NFC writing if a password has been set.
		((EditText)findViewById(R.id.password)).addTextChangedListener(new TextWatcher() {
			int beforeLength;
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				beforeLength = charSequence.length();
			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void afterTextChanged(Editable editable) {
				if(beforeLength == 0 || editable.length() == 0) {
					setWriteNfcButtonEnabled();
				}
			}
		});

		initialiseAppSpinnerView();
	}

	private void initialiseAppSpinnerView() {
		ArrayAdapter<StringWithId> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, availableAppNames);
		spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

		Spinner appsList = (Spinner)findViewById(R.id.s_keepass_app);
		appsList.setAdapter(spinnerAdapter);

		appsList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
				StringWithId selected = (StringWithId)adapterView.getItemAtPosition(i);
				dbinfo.keepassAppId = selected.id;
				storeDbInfo();
			}

			@Override
			public void onNothingSelected(AdapterView<?> adapterView) {
				dbinfo.keepassAppId = null;
				storeDbInfo();
			}
		});

		if(availableAppNames.size() == 1) {
			appsList.setVisibility(View.INVISIBLE);
		} else {
			appsList.setVisibility(VISIBLE);
		}
	}

	private static String getUriFilename(Uri uri)
	{
		/* getLastPathSegment will return a directory hierarchy if it feels like it, which seems
		 * completely wrong, so...
		 */
		String lastPathSegment = uri.getLastPathSegment();

		final String stopCharacters = ":/";
		for(int i = 0; i < stopCharacters.length(); i++) {
			int idx = lastPathSegment.lastIndexOf(stopCharacters.charAt(i));
			if(idx != -1) {
				lastPathSegment = lastPathSegment.substring(idx + 1);
			}
		}

		return lastPathSegment;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
	    case REQUEST_KEYFILE:  
	        if (resultCode == RESULT_OK) {
	            // The URI of the selected file 
	            dbinfo.keyfile = data.getData();
	            storeDbInfo();
		        ((TextView)findViewById(R.id.keyfile_name)).setText(getUriFilename(dbinfo.keyfile));
		        findViewById(R.id.b_noKeyfile).setVisibility(VISIBLE);
	        } else {
				System.err.println("REQUEST_KEYFILE result code " + resultCode);
			}
	        break;
	    case REQUEST_DATABASE:
	    	if (resultCode == RESULT_OK) {
	    		dbinfo.database = data.getData();
	    		storeDbInfo();
			    ((TextView)findViewById(R.id.database_name)).setText(getUriFilename(dbinfo.database));
	    	} else {
				System.err.println("REQUEST_DATABASE result code " + resultCode);
			}
		    setWriteNfcButtonEnabled();
	    	break;
        case REQUEST_NFC_WRITE:
            // Re-enable NFC writing.

	        switch(resultCode) {
		        case WriteNFCActivity.SUCCEEDED: {
			        Button nfc_write = (Button) findViewById(R.id.write_nfc);
			        nfc_write.setEnabled(true);

			        if (storeDbInfo()) {
				        // Job well done! Let's have some toast.
				        Toast.makeText(getApplicationContext(), "Written successfully!", Toast.LENGTH_SHORT).show();
			        } else {
				        Toast.makeText(getApplicationContext(), "Error writing to application database!", Toast.LENGTH_SHORT).show();
			        }
		        	break;
		        }
		        case WriteNFCActivity.WRONG_PIN: {
		        	// TODO this could be nicer.
			        Toast.makeText(getApplicationContext(), "Wrong PIN entered!", Toast.LENGTH_SHORT).show();
		        	break;
		        }
		        case WriteNFCActivity.NFC_ERROR: {
			        // Couldn't communicate with card.
			        Toast.makeText(getApplicationContext(), "Couldn't update!", Toast.LENGTH_SHORT).show();
			        break;
		        }
	        }
	        break;
		case REQUEST_PIN_CONFIGURATION:
			// User has configured a PIN and a previous PIN
			if(resultCode == RESULT_OK) {
				dbinfo.pin = data.getStringExtra("pin");
				dbinfo.previousPin = data.getStringExtra("previousPin");
				storeDbInfo();
				Toast.makeText(this, "PIN updated.", Toast.LENGTH_SHORT).show();
			}
			break;
		}
	}

	private void setWriteNfcButtonEnabled()
	{
		Button b = (Button) findViewById(R.id.write_nfc);
		String password = ((EditText)findViewById(R.id.password)).getText().toString();

		b.setEnabled(dbinfo.database != null && password.length() > 0);
	}

	protected boolean storeDbInfo()
	{
		dbinfo.config = DatabaseInfo.CONFIG_NOTHING; // TODO "start immediately"

		dbinfo.retainOrUpdateUriAccess(getApplicationContext());
		return dbinfo.serialise(this);
	}

	protected void switchToWriteNfcActivity()
	{
		EditText et_password = (EditText) findViewById(R.id.password);
		String password = et_password.getText().toString();

		Intent intent = new Intent(getApplicationContext(), WriteNFCActivity.class);
		intent.putExtra("password", password.getBytes());
		startActivityForResult(intent, REQUEST_NFC_WRITE);
	}

	@Override
	public void onNewIntent(Intent intent) {
		dbinfo = DatabaseInfo.deserialise(this);
		boolean startKeepass = true;

		try {
			NfcReadActions.decryptDbInfoFromIntent(this, intent, dbinfo);
		} catch(NfcReadActions.WrongPinError error) {
			// Wrong PIN
			startKeepass = false;
			Toast.makeText(this, "!!!" + error.getMessage(), Toast.LENGTH_SHORT).show();
			//switchToPinRequestActivity(REQUEST_PIN_FOR_READ);
		} catch (NfcReadActions.Error error) {
			Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
			startKeepass = false;
		}

		if(startKeepass) {
			NfcReadActions.startKeepassActivity(this, dbinfo);
		}
	}

	protected void onPinClick()
	{
		Intent intent = new Intent(getApplicationContext(), PinActivity.class);
		intent.putExtra("pin", dbinfo.pin);
		intent.putExtra("previousPin", dbinfo.previousPin);
		startActivityForResult(intent, REQUEST_PIN_CONFIGURATION);
	}

}
