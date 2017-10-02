package com.vivokey.vivokeypass.keepassapp;

import android.content.Context;
import android.content.Intent;

import com.vivokey.vivokeypass.DatabaseInfo;

public interface KeePassApp {
	String getPackageName();
	String getName();
	String getId();
	Intent getIntent(Context ctx, DatabaseInfo info);
}
