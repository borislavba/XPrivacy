package biz.bokhorst.xprivacy;

import android.content.Intent;
import android.util.Log;

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

public class XActivity extends XHook {

	private String mActionName;

	// @formatter:off

	// public void startActivities(Intent[] intents)
	// public void startActivities(Intent[] intents, Bundle options)
	// public void startActivity(Intent intent)
	// public void startActivity(Intent intent, Bundle options)
	// public void startActivityForResult(Intent intent, int requestCode)
	// public void startActivityForResult(Intent intent, int requestCode, Bundle options)
	// public void startActivityFromChild(Activity child, Intent intent, int requestCode)
	// public void startActivityFromChild(Activity child, Intent intent, int requestCode, Bundle options)
	// public void startActivityFromFragment(Fragment fragment, Intent intent, int requestCode)
	// public void startActivityFromFragment(Fragment fragment, Intent intent, int requestCode, Bundle options)
	// public boolean startActivityIfNeeded(Intent intent, int requestCode)
	// public boolean startActivityIfNeeded(Intent intent, int requestCode, Bundle options)
	// frameworks/base/core/java/android/app/Activity.java

	// @formatter:on

	public XActivity(String methodName, String restrictionName, String[] permissions, String actionName) {
		super(methodName, restrictionName, permissions);
		mActionName = actionName;
	}

	@Override
	protected void before(MethodHookParam param) throws Throwable {
		// Get intent(s)
		Intent[] intents = null;
		String methodName = param.method.getName();
		if (methodName.equals("startActivity") || methodName.equals("startActivityForResult")
				|| methodName.equals("startActivityIfNeeded"))
			intents = new Intent[] { (Intent) param.args[0] };
		else if (methodName.equals("startActivityFromChild") || methodName.equals("startActivityFromFragment"))
			intents = new Intent[] { (Intent) param.args[1] };
		else if (methodName.equals("startActivities"))
			intents = (Intent[]) param.args[0];
		else
			XUtil.log(this, Log.WARN, "Unknown method=" + methodName);

		// Process intent(s)
		if (intents != null)
			for (Intent intent : intents)
				if (mActionName.equals(intent.getAction()))
					if (isRestricted(param)) {
						if (methodName.equals("startActivityIfNeeded"))
							param.setResult(true);
						else
							param.setResult(null);
						notifyUser(mActionName);
					}
	}

	@Override
	protected void after(MethodHookParam param) throws Throwable {
		// Do nothing
	}
}
