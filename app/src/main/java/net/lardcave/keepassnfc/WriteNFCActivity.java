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

package net.lardcave.keepassnfc;

import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

import net.lardcave.keepassnfc.nfccomms.KPApplet;
import net.lardcave.keepassnfc.nfccomms.KPNdef;

public class WriteNFCActivity extends Activity {
    private static final String LOG_TAG = "WriteNFCActivity";

	private byte[] randomBytes; // Key
	private boolean writeNdefToSmartcard;

	private static class WriteTagResult {
		boolean ndefWritten;
		boolean appletWritten;
	}

	private class WriteTagTask extends AsyncTask<Intent, Integer, WriteTagResult>
	{
		@Override
		protected WriteTagResult doInBackground(Intent... ndefIntent) {
			WriteTagResult result = new WriteTagResult();

			// Attempt to access the card first as a smartcard and then as an NDEF card.
			result.appletWritten = false;
			result.ndefWritten = false;

			try {
				KPApplet applet = new KPApplet();
				result.appletWritten = applet.write(ndefIntent[0], randomBytes, writeNdefToSmartcard);
				Log.i(LOG_TAG, "Applet discovered.");
			} catch (IOException e) {
				e.printStackTrace();
				Log.i(LOG_TAG, "Couldn't communicate with applet.");
			}

			if(!result.appletWritten) {
				// try NDEF instead.
				Tag tag = ndefIntent[0].getParcelableExtra(NfcAdapter.EXTRA_TAG);
				KPNdef ndef = new KPNdef(randomBytes);
				try {
					result.ndefWritten = ndef.write(tag);
				} catch (Exception e) {
					e.printStackTrace();
					result.ndefWritten = false;
					Log.i(LOG_TAG, "Couldn't write plain NDEF");
				}
			}

			return result;
		}

		@Override
		protected void onPreExecute() {
			setUpdating(true);
		}

		@Override
		protected void onPostExecute(WriteTagResult result) {
			setUpdating(false);

			Log.i(LOG_TAG, "Write result: applet " + result.appletWritten + " NDEF only " + result.ndefWritten);

			Intent resultIntent = new Intent();
			resultIntent.putExtra("randomBytes", randomBytes);

			setResult(result.appletWritten || result.ndefWritten ? 1 : 0, resultIntent);
			finish();
		}
	}

    protected void onCreate(Bundle sis) {
        super.onCreate(sis);

		randomBytes = getIntent().getExtras().getByteArray("randomBytes");

		if(randomBytes == null) {
            throw new RuntimeException("No randombytes supplied");
        }

		if(randomBytes.length != Settings.key_length) {
			throw new RuntimeException("Unexpected key length " + randomBytes.length);
		}

		setContentView(R.layout.activity_write_nfc);

        setResult(0);

        Button b = (Button) findViewById(R.id.cancel_nfc_write_button);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View self) {
                NfcReadActions.nfc_disable(WriteNFCActivity.this);
                finish();
            }
        });

	    setUpdating(false);

    }

    private void setUpdating(boolean updating) {
	    View updating_vivokey = findViewById(R.id.updating_vivokey);
	    updating_vivokey.setVisibility(updating ? View.VISIBLE : View.INVISIBLE);
	    updating_vivokey.requestLayout(); // Android bug apparently
    }

    @Override
    protected void onResume() {
        super.onResume();

        NfcReadActions.nfc_enable(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        NfcReadActions.nfc_disable(this);
    }

    @Override
    public void onNewIntent(Intent intent)
    {
	    writeNdefToSmartcard = ((CheckBox)findViewById(R.id.cbWriteNDEF)).isChecked();

	    String action = intent.getAction();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)
				|| NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {

	        new WriteTagTask().execute(intent);
        }
    }

}
