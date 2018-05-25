package com.vivokey.vivokeypass;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

	    findViewById(R.id.bConfigure).setOnClickListener(new View.OnClickListener() {
		    @Override
		    public void onClick(View view) {
			    startTagConfiguration();
		    }
	    });

	    if(DatabaseInfo.deserialise(this) == null) {
            /* Couldn't deserialise because this is a new install, so configure. */
		    startTagConfiguration();
	    }
    }

	@Override
	public void onNewIntent(Intent intent) {
		DatabaseInfo dbinfo = DatabaseInfo.deserialise(this);

		try {
			dbinfo = NfcReadActions.decryptDbInfoFromIntent(this, intent, dbinfo);
		} catch (NfcReadActions.Error error) {
			Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
		}

		if(dbinfo != null) {
			NfcReadActions.startKeepassActivity(this, dbinfo);
		}
	}

	private void startTagConfiguration() {
	    Intent intent = new Intent();
	    intent.setClass(this, PrepareNewTagActivity.class);
	    startActivity(intent);
	    finish();
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

}
