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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.Cipher;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;

import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;
import android.content.Context;

import com.vivokey.vivokeypass.keepassapp.KeePassApps;

/* Represents the on-disk database info including encrypted password */

public class DatabaseInfo {
	public static final int CONFIG_NOTHING = 0;
	public static final int CONFIG_PASSWORD_ASK = 1;
	public static final int CONFIG_DBVERSION_EXTENDED = 0x80; // Indicates newer DB version with more fields

	// Version 4: add previousPin.
	private static final int DB_VERSION = 4;

	public Uri database;
	public Uri keyfile;
	public String pin;
	public String previousPin;
	public String password;
	public String keepassAppId;

	public int config;

	private static final String CIPHER = "AES/CBC/NoPadding";
    private static final String LOG_TAG = "keepassnfc";
	
	private DatabaseInfo(Uri database, Uri keyfile, String password, String pin, String previousPin, int config, String keepassAppId)
	{
		this.database = database;
		this.keyfile = keyfile;
		this.password = password;
		this.pin = pin;
		this.previousPin = previousPin;
		this.config = config;
		this.keepassAppId = keepassAppId;
	}

	public String getKeepassAppId() {
		return keepassAppId;
	}

	private static Cipher get_cipher(byte[] key, int mode) throws CryptoFailedException
	{
		try {
			SecretKeySpec sks = new SecretKeySpec(key, "AES");
			Cipher cipher = Cipher.getInstance(CIPHER);
			// No IV as key is never re-used
			byte[] iv_bytes = new byte[cipher.getBlockSize()]; // zeroes
			IvParameterSpec iv = new IvParameterSpec(iv_bytes);

			cipher.init(mode, sks, iv);

			return cipher;
		} catch (java.security.NoSuchAlgorithmException e) {
			Log.d(LOG_TAG, "NoSuchAlgorithm");
			throw new CryptoFailedException();
		} catch (java.security.InvalidKeyException e) {
			Log.d(LOG_TAG, "InvalidKey");
			throw new CryptoFailedException();
		} catch (javax.crypto.NoSuchPaddingException e) {
			Log.d(LOG_TAG, "NoSuchPadding");
			throw new CryptoFailedException();
		} catch (java.security.InvalidAlgorithmParameterException e) {
			Log.d(LOG_TAG, "InvalidAlgorithmParameter");
			throw new CryptoFailedException();
		}
	}

	/*
	private byte[] encrypt_password(byte[] key) throws CryptoFailedException
	{
		int i;
		int idx = 0;
		byte[] plaintext_password = password.getBytes();
		SecureRandom rng = new SecureRandom();		
		
		// Password length...
		encrypted_password[idx ++] = (byte)password.length();
		// ... and password itself...
		for (i = 0; i < plaintext_password.length; i++)
			encrypted_password[idx ++] = plaintext_password[i];
		// ... and random bytes to pad.
		while (idx < encrypted_password.length)
			encrypted_password[idx++] = (byte)rng.nextInt();
		
		// Encrypt everything
		Cipher cipher = get_cipher(key, Cipher.ENCRYPT_MODE);
		try {
			return cipher.doFinal(encrypted_password);
		} catch (javax.crypto.IllegalBlockSizeException e) {
			Log.d(LOG_TAG, "IllegalBlockSize");
			throw new CryptoFailedException();
		} catch (javax.crypto.BadPaddingException e) {
			Log.d(LOG_TAG, "BadPadding");
			throw new CryptoFailedException();
		}
	}
	*/

	String set_decrypted_password(byte[] decrypted_bytes) {
		password = new String(decrypted_bytes, 0, decrypted_bytes.length);
		return password;
	}

	/*
	String decrypt_password(byte[] key) throws CryptoFailedException
	{
		byte[] decrypted;
		Cipher cipher = get_cipher(key, Cipher.DECRYPT_MODE);

		try {
			decrypted = cipher.doFinal(encrypted_password);
		} catch (javax.crypto.IllegalBlockSizeException e) {
			Log.d(LOG_TAG, "IllegalBlockSize");
			throw new CryptoFailedException();
		} catch (javax.crypto.BadPaddingException e) {
			Log.d(LOG_TAG, "BadPadding");
			throw new CryptoFailedException();
		}

		return set_decrypted_password(decrypted);
	}
	*/
	
	// Persist access to the file.
	private void persistAccessToFile(Context ctx, Uri uri) {
		// https://developer.android.com/guide/topics/providers/document-provider.html#permissions
		// via http://stackoverflow.com/a/21640230
		if(DocumentsContract.isDocumentUri(ctx, uri)) {
			ctx.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
		} else {
			Log.e(LOG_TAG, "URI " + uri.toString() + " is not a document and can't be persisted");
		}
	}

	void retainOrUpdateUriAccess(Context ctx) {
		if(database != null)
			persistAccessToFile(ctx, database);

		if(keyfile != null)
			persistAccessToFile(ctx, keyfile);
	}

	boolean serialise(Context ctx)
	{
		/* Store the configuration on the Android device.  */
		FileOutputStream configuration;

		try {
			configuration = ctx.openFileOutput(Settings.nfcinfo_filename_template + "_00.txt", Context.MODE_PRIVATE);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		}

		try {
			configuration.write(config | CONFIG_DBVERSION_EXTENDED);
			write_string(configuration, database == null ? "" : database.toString());
			write_string(configuration, keyfile == null ? "" : keyfile.toString());
			configuration.write(DB_VERSION);
			write_string(configuration, keepassAppId);
			write_string(configuration, pin == null ? "" : pin);
			write_string(configuration, previousPin == null ? "" : previousPin);

			configuration.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	static DatabaseInfo deserialise(Context ctx)
	{
		int config;
		String keepassAppId = KeePassApps.getDefaultApp().getId();
		String databaseString, keyfileString, pinString = null, previousPinString = null;

		FileInputStream nfcinfo;

		try {
			nfcinfo = ctx.openFileInput(Settings.nfcinfo_filename_template + "_00.txt");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return getDefaultConfig();
		}
		
		try {
			config = nfcinfo.read();
			boolean extended_db_info = ((config & CONFIG_DBVERSION_EXTENDED) != 0);

			databaseString = read_string(nfcinfo);
			keyfileString = read_string(nfcinfo);

			if(extended_db_info) {
				int version = nfcinfo.read();
				if(version != DB_VERSION) {
					Log.e(LOG_TAG, "Unknown database version");
					return getDefaultConfig();
				}
				keepassAppId = read_string(nfcinfo);
				pinString = read_string(nfcinfo);
				previousPinString = read_string(nfcinfo);
			}
		} catch (IOException e) {
			e.printStackTrace();
			return getDefaultConfig();
		}

		Uri database = databaseString.equals("") ? null: Uri.parse(databaseString);
		Uri keyfile = keyfileString.equals("") ? null: Uri.parse(keyfileString);
		
		return new DatabaseInfo(database, keyfile, null, pinString, previousPinString, config, keepassAppId);
	}

	static DatabaseInfo getDefaultConfig()
	{
		return new DatabaseInfo(null, null, null, null, null, CONFIG_NOTHING, null);

	}

	private static short read_short(FileInputStream fis) throws IOException
	{
		byte[] bytes = new byte[2];
		short[] shorts = new short[1];
		if(fis.read(bytes, 0, 2) != 2) {
            throw new IOException("Short read while reading short");
        }

		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
		return shorts[0];
	}

	private static byte[] read_bytes(FileInputStream fis) throws IOException
	{
		int length = read_short(fis);

		byte[] buffer = new byte[length];
		int actual_length = fis.read(buffer, 0, length);

        if(actual_length != length) {
            throw new IOException("read_bytes: couldn't read desired length");
        }

		return buffer;
	}
	
	private static String read_string(FileInputStream fis) throws IOException
	{
		byte[] stringBytes = read_bytes(fis);
		return new String(stringBytes, 0, stringBytes.length);
	}

	private static void write_string(FileOutputStream stream, String s) throws IOException {
		stream.write(to_short(s.length()));
		stream.write(s.getBytes());
	}

	private static byte[] to_short(int i)
	{
		return to_short((short)i);
	}

	private static byte[] to_short(short i)
	{
		byte[] bytes = new byte[2];
		short[] shorts = {i};

		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts);

		return bytes;
	}

}
