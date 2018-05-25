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

import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

import com.vivokey.vivokeypass.nfccomms.KPApplet;

public class WriteNFCActivity extends Activity implements WriteNFCActivityCaller {
    private static final String LOG_TAG = "WriteNFCActivity";

	private static boolean writeNdefToSmartcard = false;
	private DatabaseInfo dbinfo;

	// Password is stored separately as it is not serialised by dbinfo.
	private byte[] password;

	public static final int SUCCEEDED = 0, WRONG_PIN = 1, NFC_ERROR = 2;

	private static class WriteTagResult {
		int result;
	}

	private static class WriteTagTask extends AsyncTask<Intent, Integer, WriteTagResult>
	{
		private WriteNFCActivityCaller caller;
		private byte[] password;
		private byte[] previousPin;
		private byte[] pin;

		WriteTagTask(WriteNFCActivityCaller caller, byte[] password, byte[] previousPin, byte[] pin)
		{
			super();
			this.caller = caller;
			this.password = password;
			this.previousPin = previousPin;
			this.pin = pin;
		}

		@Override
		protected WriteTagResult doInBackground(Intent... ndefIntent) {
			WriteTagResult result = new WriteTagResult();

			// Attempt to access the card first as a smartcard and then as an NDEF card.
			result.result = WriteNFCActivity.SUCCEEDED;

			try {
				KPApplet applet = new KPApplet();
				if(applet.setSecretData(ndefIntent[0], password, pin, previousPin, writeNdefToSmartcard))
					result.result = WriteNFCActivity.SUCCEEDED;
			} catch (IOException e) {
				e.printStackTrace();
				Log.i(LOG_TAG, "Couldn't communicate with applet.");
				result.result = WriteNFCActivity.NFC_ERROR;
			} catch (KPApplet.WrongPinError wrongPinError) {
				result.result = WriteNFCActivity.WRONG_PIN;
				wrongPinError.printStackTrace();
			}

			// We can't do plain NDEF for now.
			return result;
		}

		@Override
		protected void onPreExecute() {
			caller.setUpdating(true);
		}

		@Override
		protected void onPostExecute(WriteTagResult result) {
			caller.setUpdating(false);

			Log.i(LOG_TAG, "Write result: " + result.result);

			caller.finished(result.result);
		}
	}

	public void finished(int result) {
		setResult(result);
		finish();
	}

    protected void onCreate(Bundle sis) {
        super.onCreate(sis);

        Bundle extras = getIntent().getExtras();

		password = extras.getByteArray("password");

		if(password == null) {
            throw new RuntimeException("No password supplied");
        }

	    dbinfo = DatabaseInfo.deserialise(this);
		setContentView(R.layout.activity_write_nfc);

        setResult(0);

        Button b = (Button) findViewById(R.id.cancel_nfc_write_button);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View self) {
                NfcReadActions.nfc_disable(WriteNFCActivity.this);
	            Intent resultIntent = new Intent();
	            resultIntent.putExtra("password", password);

	            setResult(0, resultIntent);
                finish();
            }
        });

	    setUpdating(false);

    }

	public void setUpdating(boolean updating) {
	    View updating_vivokey = findViewById(R.id.updating_vivokey);
	    updating_vivokey.setVisibility(updating ? View.VISIBLE : View.INVISIBLE);
	    updating_vivokey.requestLayout(); // Android bug apparently
    }

    @Override
    protected void onResume() {
        super.onResume();

	    ((CheckBox)findViewById(R.id.cbWriteNDEF)).setChecked(writeNdefToSmartcard);
        NfcReadActions.nfc_enable(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

	    writeNdefToSmartcard = ((CheckBox)findViewById(R.id.cbWriteNDEF)).isChecked();
        NfcReadActions.nfc_disable(this);
    }

    @Override
    public void onNewIntent(Intent intent)
    {
	    writeNdefToSmartcard = ((CheckBox)findViewById(R.id.cbWriteNDEF)).isChecked();

	    String action = intent.getAction();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)
				|| NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {

        	byte[] pin = dbinfo.pin.getBytes();
        	byte[] previousPin = dbinfo.previousPin.getBytes();

	        new WriteTagTask(this, password, previousPin, pin).execute(intent);
        }
    }

}
