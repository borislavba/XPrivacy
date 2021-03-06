package biz.bokhorst.xprivacy;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import de.robv.android.xposed.XSharedPreferences;

public class XPrivacyProvider extends ContentProvider {

	public static final String AUTHORITY = "biz.bokhorst.xprivacy.provider";
	public static final String PREF_RESTRICTION = AUTHORITY;
	public static final String PREF_USAGE = AUTHORITY + ".usage";
	public static final String PREF_SETTINGS = AUTHORITY + ".settings";
	public static final String PATH_RESTRICTION = "restriction";
	public static final String PATH_USAGE = "usage";
	public static final String PATH_AUDIT = "audit";
	public static final String PATH_SETTINGS = "settings";
	public static final Uri URI_RESTRICTION = Uri.parse("content://" + AUTHORITY + "/" + PATH_RESTRICTION);
	public static final Uri URI_USAGE = Uri.parse("content://" + AUTHORITY + "/" + PATH_USAGE);
	public static final Uri URI_AUDIT = Uri.parse("content://" + AUTHORITY + "/" + PATH_AUDIT);
	public static final Uri URI_SETTING = Uri.parse("content://" + AUTHORITY + "/" + PATH_SETTINGS);

	public static final String COL_UID = "Uid";
	public static final String COL_RESTRICTION = "Restriction";
	public static final String COL_RESTRICTED = "Restricted";
	public static final String COL_METHOD = "Method";
	public static final String COL_USED = "Used";
	public static final String COL_SETTING = "Setting";
	public static final String COL_ENABLED = "Enabled";

	private static final UriMatcher sUriMatcher;
	private static final int TYPE_RESTRICTION = 1;
	private static final int TYPE_USAGE = 2;
	private static final int TYPE_AUDIT = 3;
	private static final int TYPE_SETTING = 4;

	static {
		sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		sUriMatcher.addURI(AUTHORITY, PATH_RESTRICTION, TYPE_RESTRICTION);
		sUriMatcher.addURI(AUTHORITY, PATH_USAGE, TYPE_USAGE);
		sUriMatcher.addURI(AUTHORITY, PATH_AUDIT, TYPE_AUDIT);
		sUriMatcher.addURI(AUTHORITY, PATH_SETTINGS, TYPE_SETTING);
	}

	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public String getType(Uri uri) {
		if (sUriMatcher.match(uri) == TYPE_RESTRICTION)
			return String.format("vnd.android.cursor.dir/%s.%s", AUTHORITY, PATH_RESTRICTION);
		else if (sUriMatcher.match(uri) == TYPE_USAGE)
			return String.format("vnd.android.cursor.dir/%s.%s", AUTHORITY, PATH_USAGE);
		else if (sUriMatcher.match(uri) == TYPE_AUDIT)
			return String.format("vnd.android.cursor.dir/%s.%s", AUTHORITY, PATH_AUDIT);
		else if (sUriMatcher.match(uri) == TYPE_SETTING)
			return String.format("vnd.android.cursor.dir/%s.%s", AUTHORITY, PATH_SETTINGS);
		throw new IllegalArgumentException();
	}

	@Override
	@SuppressWarnings("deprecation")
	@SuppressLint("WorldReadableFiles")
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		if (sUriMatcher.match(uri) == TYPE_RESTRICTION && selectionArgs != null && selectionArgs.length >= 2) {
			// Get arguments
			String restrictionName = selection;
			int uid = Integer.parseInt(selectionArgs[0]);
			boolean usage = Boolean.parseBoolean(selectionArgs[1]);
			String methodName = (selectionArgs.length >= 3 ? selectionArgs[2] : null);

			// Update usage count
			if (usage) {
				long timestamp = new Date().getTime();
				SharedPreferences uprefs = getContext().getSharedPreferences(PREF_USAGE, Context.MODE_PRIVATE);
				SharedPreferences.Editor editor = uprefs.edit();
				editor.putLong(getUsagePref(uid, restrictionName), timestamp);
				if (methodName != null)
					editor.putLong(getMethodPref(uid, restrictionName, methodName), timestamp);
				editor.commit();
			}

			// Get restrictions
			SharedPreferences prefs = getContext().getSharedPreferences(PREF_RESTRICTION, Context.MODE_WORLD_READABLE);
			boolean allowed = getAllowed(uid, restrictionName, prefs);

			// Return restriction
			MatrixCursor cursor = new MatrixCursor(new String[] { COL_UID, COL_RESTRICTION, COL_RESTRICTED });
			cursor.addRow(new Object[] { uid, restrictionName, Boolean.toString(!allowed) });
			return cursor;
		} else if (sUriMatcher.match(uri) == TYPE_USAGE && selectionArgs != null && selectionArgs.length == 1) {
			// Return usage
			String restrictionName = selection;
			int uid = Integer.parseInt(selectionArgs[0]);
			SharedPreferences prefs = getContext().getSharedPreferences(PREF_USAGE, Context.MODE_PRIVATE);
			MatrixCursor cursor = new MatrixCursor(new String[] { COL_UID, COL_RESTRICTION, COL_USED });
			cursor.addRow(new Object[] { uid, restrictionName, prefs.getLong(getUsagePref(uid, restrictionName), 0) });
			return cursor;
		} else if (sUriMatcher.match(uri) == TYPE_AUDIT && selectionArgs != null && selectionArgs.length == 1) {
			// Return audit
			String restrictionName = selection;
			int uid = Integer.parseInt(selectionArgs[0]);
			String prefix = getUsagePref(uid, restrictionName) + ".";
			MatrixCursor cursor = new MatrixCursor(new String[] { COL_UID, COL_RESTRICTION, COL_METHOD, COL_USED });
			SharedPreferences prefs = getContext().getSharedPreferences(PREF_USAGE, Context.MODE_PRIVATE);
			for (String pref : prefs.getAll().keySet())
				if (pref.startsWith(prefix))
					cursor.addRow(new Object[] { uid, restrictionName, pref.substring(prefix.length()),
							prefs.getLong(pref, 0) });
			return cursor;
		} else if (sUriMatcher.match(uri) == TYPE_SETTING && selectionArgs == null) {
			// Return setting
			String settingName = selection;
			SharedPreferences prefs = getContext().getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE);
			MatrixCursor cursor = new MatrixCursor(new String[] { COL_SETTING, COL_ENABLED });
			cursor.addRow(new Object[] { settingName, prefs.getBoolean(getSettingPref(settingName), false) });
			return cursor;
		}
		throw new IllegalArgumentException();
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		// Check access
		enforcePermission();

		throw new IllegalArgumentException();
	}

	@Override
	@SuppressWarnings("deprecation")
	@SuppressLint("WorldReadableFiles")
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		// Check access
		enforcePermission();

		if (sUriMatcher.match(uri) == TYPE_RESTRICTION) {
			// Get arguments
			String restrictionName = selection;
			int uid = values.getAsInteger(COL_UID);
			boolean allowed = !Boolean.parseBoolean(values.getAsString(COL_RESTRICTED));

			// Get restrictions
			SharedPreferences prefs = getContext().getSharedPreferences(PREF_RESTRICTION, Context.MODE_WORLD_READABLE);
			String restrictions = prefs.getString(getRestrictionPref(restrictionName), "*");

			// Decode restrictions
			List<String> listRestriction = new ArrayList<String>(Arrays.asList(restrictions.split(",")));
			boolean defaultAllowed = listRestriction.get(0).equals("*");

			// Allow or deny
			String sUid = Integer.toString(uid);
			if (defaultAllowed ? allowed : !allowed)
				listRestriction.remove(sUid);
			if (defaultAllowed ? !allowed : allowed)
				if (!listRestriction.contains(sUid))
					listRestriction.add(sUid);

			// Encode restrictions
			restrictions = TextUtils.join(",", listRestriction.toArray(new String[0]));

			// Update restriction
			SharedPreferences.Editor editor = prefs.edit();
			editor.putString(getRestrictionPref(restrictionName), restrictions);
			editor.commit();
			setPrefFileReadable(PREF_RESTRICTION);

			return 1; // rows
		} else if (sUriMatcher.match(uri) == TYPE_SETTING) {
			// Get arguments
			String settingName = selection;

			// Update setting
			boolean enabled = Boolean.parseBoolean(values.getAsString(COL_ENABLED));
			SharedPreferences prefs = getContext().getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = prefs.edit();
			editor.putBoolean(getSettingPref(settingName), enabled);
			editor.commit();

			return 1;
		}
		throw new IllegalArgumentException(uri.toString());
	}

	@Override
	public int delete(Uri uri, String where, String[] selectionArgs) {
		// Check access
		enforcePermission();

		if (sUriMatcher.match(uri) == TYPE_AUDIT && selectionArgs != null && selectionArgs.length == 1) {
			// Get arguments
			String restrictionName = where;
			int uid = Integer.parseInt(selectionArgs[0]);

			// Delete audit trail
			int rows = 0;
			String prefix = getUsagePref(uid, restrictionName);
			SharedPreferences prefs = getContext().getSharedPreferences(PREF_USAGE, Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = prefs.edit();
			for (String pref : prefs.getAll().keySet())
				if (pref.startsWith(prefix)) {
					rows++;
					editor.remove(pref);
					XUtil.log(null, Log.INFO, "Removed audit=" + pref);
				}
			editor.commit();
			return rows;
		}
		throw new IllegalArgumentException();
	}

	// Public helper methods

	public static void setPrefFileReadable(String preference) {
		new File(getPrefFileName(preference)).setReadable(true, false);
	}

	// The following method is used as fallback, when:
	// - there is no context (Java threads)
	// - the content provider cannot be queried (PackageManagerService)

	public static boolean getRestrictedFallback(XHook hook, int uid, String restrictionName) {
		// Get restrictions
		XSharedPreferences xprefs = new XSharedPreferences(new File(getPrefFileName(PREF_RESTRICTION)));
		return !getAllowed(uid, restrictionName, xprefs);
	}

	// Private helper methods

	private void enforcePermission() throws SecurityException {
		// Only XPrivacy can insert, update or delete
		int cuid = Binder.getCallingUid();
		String[] packages = getContext().getPackageManager().getPackagesForUid(cuid);
		List<String> listPackage = new ArrayList<String>(Arrays.asList(packages));
		String self = XPrivacyProvider.class.getPackage().getName();
		if (!listPackage.contains(self))
			throw new SecurityException();
	}

	private static boolean getAllowed(int uid, String restrictionName, SharedPreferences prefs) {
		// Get restrictions
		String restrictions = prefs.getString(getRestrictionPref(restrictionName), "*");

		// Decode restrictions
		List<String> listRestriction = new ArrayList<String>(Arrays.asList(restrictions.split(",")));
		boolean defaultRestricted = listRestriction.get(0).equals("*");

		// Check if restricted
		boolean allowed = !listRestriction.contains(Integer.toString(uid));
		if (!defaultRestricted)
			allowed = !allowed;
		return allowed;
	}

	private static String getPrefFileName(String preference) {
		String packageName = XRestriction.class.getPackage().getName();
		return Environment.getDataDirectory() + "/data/" + packageName + "/shared_prefs/" + preference + ".xml";
	}

	private static String getRestrictionPref(String restrictionName) {
		return COL_RESTRICTED + "." + restrictionName;
	}

	private static String getUsagePref(int uid, String restrictionName) {
		return COL_USED + "." + uid + "." + restrictionName;
	}

	private static String getMethodPref(int uid, String restrictionName, String methodName) {
		return getUsagePref(uid, restrictionName) + "." + methodName;
	}

	private static String getSettingPref(String settingName) {
		return COL_SETTING + "." + settingName;
	}
}
