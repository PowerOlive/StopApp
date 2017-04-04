package com.sscience.stopapp.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.widget.Toast;

import com.science.myloggerlibrary.MyLogger;
import com.sscience.stopapp.R;
import com.sscience.stopapp.activity.ShortcutActivity;
import com.sscience.stopapp.database.AppInfoDBController;
import com.sscience.stopapp.database.AppInfoDBOpenHelper;
import com.sscience.stopapp.model.AppsRepository;
import com.sscience.stopapp.model.GetRootCallback;
import com.sscience.stopapp.util.CommonUtil;
import com.sscience.stopapp.util.SharedPreferenceUtil;
import com.sscience.stopapp.util.ShortcutsManager;

import java.util.HashSet;
import java.util.Set;

import static com.sscience.stopapp.activity.SettingActivity.SP_AUTO_DISABLE_APPS;
import static com.sscience.stopapp.model.AppsRepository.COMMAND_ENABLE;

/**
 * @author SScience
 * @description
 * @email chentushen.science@gmail.com
 * @data 2017/2/8
 */

public class RootActionIntentService extends IntentService {

    private Handler mHandler;
    public static final String APP_SHORTCUT_PACKAGE_NAME = "app_shortcut_package_name";

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     * <p>
     * Used to name the worker thread, important only for debugging.
     */
    public RootActionIntentService() {
        super("RootActionIntentService");
        mHandler = new Handler();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String packageName = intent.getStringExtra(ShortcutActivity.EXTRA_PACKAGE_NAME);
        MyLogger.e("-----------" + packageName);
        launchAppIntent(packageName);
        stopSelf();
    }

    private void launchAppIntent(String packageName) {
        if (!CommonUtil.isAppInstalled(RootActionIntentService.this, packageName)) {
            ShortcutsManager manager = new ShortcutsManager(RootActionIntentService.this);
            manager.removeShortcut(packageName, getString(R.string.app_had_uninstall));
        } else {
            try {
                Intent resolveIntent = getPackageManager().getLaunchIntentForPackage(packageName);
                startActivity(resolveIntent);
            } catch (NullPointerException e) {
                enableApp(AppsRepository.COMMAND_ENABLE, packageName);
            }
            boolean spAutoDisable = (boolean) SharedPreferenceUtil.get(this, SP_AUTO_DISABLE_APPS, false);
            if (spAutoDisable) {
                AppsRepository appsRepository = new AppsRepository(this);
                appsRepository.openAccessibilityServices(null);
            }
        }
    }

    private void enableApp(final String cmd, final String packageName) {
        new AppsRepository(RootActionIntentService.this).getRoot(cmd + packageName, new GetRootCallback() {

            @Override
            public void onRoot(boolean isRoot) {
                if (isRoot) {
                    // 已停用的app以Shortcut形势启动，则需更新主页app为启用
                    Set<String> packageSet = new HashSet<>();
                    packageSet = (Set<String>) SharedPreferenceUtil.get(RootActionIntentService.this
                            , RootActionIntentService.APP_SHORTCUT_PACKAGE_NAME, packageSet);
                    packageSet.add(packageName);
                    SharedPreferenceUtil.put(RootActionIntentService.this, APP_SHORTCUT_PACKAGE_NAME, packageSet);
                    AppInfoDBController appInfoDBController = new AppInfoDBController(RootActionIntentService.this);
                    appInfoDBController.updateDisableApp(packageName, 1, AppInfoDBOpenHelper.TABLE_NAME_APP_INFO);
                    launchAppIntent(packageName);
                } else {
                    mHandler.post(new DisplayToast(RootActionIntentService.this
                            , getString(R.string.if_want_to_use_please_grant_app_root)));
                }
            }
        });
    }

    public class DisplayToast implements Runnable {
        private final Context mContext;
        String mText;

        public DisplayToast(Context mContext, String text) {
            this.mContext = mContext;
            mText = text;
        }

        public void run() {
            Toast.makeText(mContext, mText, Toast.LENGTH_SHORT).show();
        }
    }
}
