package com.vivokey.vivokeypass;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

public class PinActivity extends Activity {
	EditText etPin;
	EditText etPreviousPin;

	@Override
	protected void onCreate(Bundle sis) {
		super.onCreate(sis);
		setContentView(R.layout.pin);

		etPin = (EditText)findViewById(R.id.et_new_pin);
		etPreviousPin = (EditText)findViewById(R.id.et_current_pin);

		(findViewById(R.id.b_continue)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				finish(true);
			}
		});

		(findViewById(R.id.b_cancel)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				finish(false);
			}
		});

		if (sis != null) {
			setPin(sis.getString("pin"));
			setPreviousPin(sis.getString("previousPin"));
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		setPin(getIntent().getStringExtra("pin"));
		setPreviousPin(getIntent().getStringExtra("previousPin"));
	}

	void finish(boolean success)
	{
		Intent resultData = new Intent();

		resultData.putExtra("pin", getPin());
		resultData.putExtra("previousPin", getPreviousPin());
		setResult(success ? RESULT_OK : RESULT_CANCELED, resultData);
		finish();
	}

	private void setPin(String pin)
	{
		etPin.setText(pin);
	}

	private String getPin()
	{
		return etPin.getText().toString();
	}

	private void setPreviousPin(String pin)
	{
		etPreviousPin.setText(pin);
	}

	private String getPreviousPin()
	{
		return etPreviousPin.getText().toString();
	}

	@Override
	protected void onSaveInstanceState(Bundle sis)
	{
		super.onSaveInstanceState(sis);
		sis.putString("pin", getPin());
		sis.putString("previousPin", getPreviousPin());
	}

}
