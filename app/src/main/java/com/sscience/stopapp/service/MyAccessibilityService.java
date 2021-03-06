package com.sscience.stopapp.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import com.science.myloggerlibrary.MyLogger;
import com.sscience.stopapp.R;
import com.sscience.stopapp.activity.SettingActivity;
import com.sscience.stopapp.database.AppInfoDBController;
import com.sscience.stopapp.database.AppInfoDBOpenHelper;
import com.sscience.stopapp.model.AppsRepository;
import com.sscience.stopapp.model.GetRootCallback;
import com.sscience.stopapp.presenter.DisableAppsPresenter;
import com.sscience.stopapp.util.CommonUtil;
import com.sscience.stopapp.util.SharedPreferenceUtil;

/**
 * @author SScience
 * @description
 * @email chentushen.science@gmail.com
 * @data 2017/4/2
 */
public class MyAccessibilityService extends AccessibilityService {

    private AppInfoDBController mDBController;
    private AppsRepository mAppsRepository;
    private CountDownTimer mCountDownTimer;
    private String foregroundPackageName;
    private boolean isActionBack;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = (String) SharedPreferenceUtil.get(this, DisableAppsPresenter.SP_LAUNCH_APP, "");
            int spAutoDisable = (int) SharedPreferenceUtil.get(this, SettingActivity.SP_AUTO_DISABLE_APPS, -1);
            if (spAutoDisable != -1 && spAutoDisable == 666) {
                actionBackDisableApp(event, packageName);
            }
            foregroundPackageName = event.getPackageName().toString();
            if (spAutoDisable != -1 && spAutoDisable != 666) {
                actionHomeDisableApp(spAutoDisable, foregroundPackageName, packageName);
            }
        }
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                isActionBack = true;
                break;

            default:
                isActionBack = false;
                break;
        }
        return super.onKeyEvent(event);
    }

    /**
     * 按返回键推出app自动冻结(目前只支持物理返回按键）
     */
    private void actionBackDisableApp(AccessibilityEvent event, final String packageName) {
        if (!TextUtils.equals(foregroundPackageName, packageName)) {
            // 如果上一个前台应用不是启动的停用app，表明早已退出启动的停用app
            return;
        }
        // 获取当前的前台应用，如果当前的前台应用不是启动的停用app，且是按了返回键，则是按返回键退出app
        foregroundPackageName = event.getPackageName().toString();
        if (!TextUtils.equals(foregroundPackageName, packageName) && isActionBack) {
            isActionBack = false;
            mAppsRepository.getRoot(AppsRepository.COMMAND_DISABLE + packageName, new GetRootCallback() {
                @Override
                public void onRoot(boolean isRoot) {
                    if (isRoot) {
                        MyLogger.e("已退出App自动冻结的App：" + packageName);
                        updateDisableApp(packageName);
                    } else {
                        Toast.makeText(MyAccessibilityService.this, getString(R.string.if_want_to_use_please_grant_app_root), Toast.LENGTH_SHORT).show();
                    }
                }
            });
            mAppsRepository.cancelTask();
        }
    }

    /**
     * 回到桌面自动冻结(可一定时间后冻结)
     */
    private void actionHomeDisableApp(int spAutoDisable, String foregroundPackageName, final String packageName) {
        if (TextUtils.equals(foregroundPackageName, packageName)) {
            if (mCountDownTimer != null) {
                mCountDownTimer.cancel();
                mCountDownTimer = null;
            }
        }
        if (TextUtils.isEmpty(packageName) || !TextUtils.equals(foregroundPackageName, CommonUtil.getLauncherPackageName(this))) {
            return;
        }
        if (mCountDownTimer == null) {
            mCountDownTimer = new CountDownTimer(spAutoDisable * 1000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                }

                @Override
                public void onFinish() {
                    mAppsRepository.getRoot(AppsRepository.COMMAND_DISABLE + packageName, new GetRootCallback() {
                        @Override
                        public void onRoot(boolean isRoot) {
                            if (isRoot) {
                                MyLogger.e("回到桌面已自动冻结的App：" + packageName);
                                updateDisableApp(packageName);
                            } else {
                                Toast.makeText(MyAccessibilityService.this, getString(R.string.if_want_to_use_please_grant_app_root), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    mAppsRepository.cancelTask();
                }
            };
            mCountDownTimer.start();
        }
    }

    private void updateDisableApp(String packageName) {
        // 切换到主界面时，更新app列表
        SharedPreferenceUtil.put(MyAccessibilityService.this, RootActionIntentService.APP_UPDATE_HOME_APPS, true);
        // 避免重复更新
        SharedPreferenceUtil.put(MyAccessibilityService.this, DisableAppsPresenter.SP_LAUNCH_APP, "");
        mDBController.updateDisableApp(packageName, 0, AppInfoDBOpenHelper.TABLE_NAME_APP_INFO);
    }

    @Override
    protected void onServiceConnected() {
        MyLogger.e("无障碍服务已开启");
        mDBController = new AppInfoDBController(this);
        mAppsRepository = new AppsRepository(this);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        MyLogger.e("无障碍服务已关闭");
        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
            mCountDownTimer = null;
        }
        mDBController = null;
        mAppsRepository = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onInterrupt() {

    }
}
