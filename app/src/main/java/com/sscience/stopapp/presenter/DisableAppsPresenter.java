package com.sscience.stopapp.presenter;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;

import com.science.myloggerlibrary.MyLogger;
import com.sscience.stopapp.R;
import com.sscience.stopapp.activity.MainActivity;
import com.sscience.stopapp.bean.AppInfo;
import com.sscience.stopapp.database.AppInfoDBController;
import com.sscience.stopapp.database.AppInfoDBOpenHelper;
import com.sscience.stopapp.model.AppsRepository;
import com.sscience.stopapp.util.AppInfoComparator;
import com.sscience.stopapp.util.SharedPreferenceUtil;
import com.sscience.stopapp.util.ShortcutsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.sscience.stopapp.model.AppsRepository.COMMAND_APP_LIST;
import static com.sscience.stopapp.model.AppsRepository.COMMAND_DISABLE;
import static com.sscience.stopapp.model.AppsRepository.COMMAND_ENABLE;
import static com.sscience.stopapp.model.AppsRepository.COMMAND_UNINSTALL;

/**
 * @author SScience
 * @description 首页停用（待停用）apps列表
 * @email chentushen.science@gmail.com
 * @data 2017/1/15
 */

public class DisableAppsPresenter implements DisableAppsContract.Presenter {

    public static final String SP_DISABLE_APPS = "sp_disable_apps";
    public static final int APP_STYLE_ALL = 0;
    public static final int APP_STYLE_SYSTEM = 1;
    public static final int APP_STYLE_USER = 2;
    private DisableAppsContract.View mView;
    private AppsRepository mAppsRepository;
    private List<AppInfo> mListDisableApps;
    private List<AppInfo> mListDisableAppsNew;
    private Activity mActivity;
    private ShortcutsManager mShortcutsManager;
    private AppInfoDBController mAppInfoDBController;
    private boolean isFirstRoot = true;

    public DisableAppsPresenter(Activity activity, DisableAppsContract.View view) {
        mActivity = activity;
        mView = view;
        mView.setPresenter(this);
        mListDisableApps = new ArrayList<>();
        mListDisableAppsNew = new ArrayList<>();
        mAppsRepository = new AppsRepository(activity);
        mShortcutsManager = new ShortcutsManager(activity);
        mAppInfoDBController = new AppInfoDBController(activity);
    }

    @Override
    public void start() {
        List<AppInfo> disableApps = mAppInfoDBController.getDisableApps(AppInfoDBOpenHelper.TABLE_NAME_APP_INFO);
        if (disableApps.isEmpty() && disableApps.size() == 0) {
            commandSu(COMMAND_APP_LIST + "-d", false, null, -1);
        } else {
            getDisableApps(disableApps, false);
        }
    }

    @Override
    public void disableApp(final AppInfo appInfo, final int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setTitle(R.string.tip);
        builder.setMessage(mActivity.getString(R.string.whether_disable_app, appInfo.getAppName()));
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(mActivity.getString(R.string.confirm), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                appInfo.setEnable(appInfo.isEnable() == 1 ? 0 : 1);
                commandSu(appInfo.isEnable() == 1 ? COMMAND_ENABLE : COMMAND_DISABLE + appInfo.appPackageName,
                        false, null, -1);
                dialog.dismiss();
            }
        });
        builder.show();
    }

    @Override
    public void commandSu(final String cmd, final boolean isLaunchApp, final AppInfo appInfo, final int position) {
        mAppsRepository.commandSu(cmd, new AppsRepository.GetAppsCmdCallback() {
            @Override
            public void onRootAppsLoaded(List<AppInfo> apps) {
                getDisableApps(apps, true);
            }

            @Override
            public void onRootError() {
                mView.getRootError();
            }

            @Override
            public void onRootSuccess() {
                if (isLaunchApp) {
                    mListDisableApps.get(position).setEnable(1);
                    addShortcut(appInfo);
                    mView.upDateItemIfLaunch(appInfo, position);
                    launchAppIntent(appInfo.getAppPackageName());
                } else {
                    if (isFirstRoot) {
                        isFirstRoot = false;
                        mView.getRootSuccess(appInfo, mListDisableApps, mListDisableAppsNew);
                        mListDisableApps = mListDisableAppsNew;
                    }
                }
            }
        });
    }

    private void launchAppIntent(String packageName) {
        try {
            Intent resolveIntent = mActivity.getPackageManager().getLaunchIntentForPackage(packageName);
            mActivity.startActivity(resolveIntent);
        } catch (NullPointerException e) {
            MyLogger.e("启动app出错:" + e.toString());
        }
    }

    private void getDisableApps(List<AppInfo> appList, boolean isFirst) {
        mListDisableApps.clear();
        for (AppInfo appInfo : appList) {
            if (isFirst) {
                mAppInfoDBController.addDisableApp(appInfo, AppInfoDBOpenHelper.TABLE_NAME_APP_INFO);
            }
            if (appInfo.isEnable() == 1) {
                ((MainActivity) mActivity).getSelection().add(appInfo);
            }
        }
        mListDisableApps = appList;
        Collections.sort(mListDisableApps, new AppInfoComparator());// 排序
        mView.getApps(mListDisableApps);
    }

    @Override
    public List<String> getDisableAppPackageNames() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < mListDisableApps.size(); i++) {
            list.add(mListDisableApps.get(i).getAppPackageName());
        }
        return list;
    }

    @Override
    public void launchApp(AppInfo appInfo, int position) {
        if (appInfo.isEnable() == 0) {
            commandSu(COMMAND_ENABLE + appInfo.getAppPackageName(), true, appInfo, position);
            mAppInfoDBController.updateDisableApp(appInfo.getAppPackageName(), 1
                    , AppInfoDBOpenHelper.TABLE_NAME_APP_INFO);
        } else {
            addShortcut(appInfo);
            launchAppIntent(appInfo.getAppPackageName());
            mView.upDateItemIfLaunch(null, -1);
        }
    }

    private void addShortcut(AppInfo appInfo) {
        String sp = (String) SharedPreferenceUtil.get(mActivity, ShortcutsManager.SP_ADD_SHORTCUT_MODE, "");
        if (TextUtils.isEmpty(sp) || ShortcutsManager.SP_AUTO_SHORTCUT.equals(sp)) {
            mShortcutsManager.addAppShortcut(Arrays.asList(appInfo));
        }
    }

    @Override
    public void batchApps(final int type) {
        mAppsRepository.getRoot(new AppsRepository.GetRootCallback() {
            @Override
            public void onRoot(Boolean isRoot) {
                if (isRoot) {
                    try {
                        mListDisableAppsNew = new ArrayList<>();
                        for (AppInfo info : mListDisableApps) {
                            mListDisableAppsNew.add(info.clone());
                        }
                    } catch (CloneNotSupportedException e) {
                        e.printStackTrace();
                    }
                    List<AppInfo> appList = new ArrayList<>(((MainActivity) mActivity).getSelection());
                    boolean isEnable = false;
                    isFirstRoot = true;
                    for (int i = 0; i < appList.size(); i++) {
                        AppInfo appInfo = appList.get(i);
                        if (type == 0) { // 停用应用
                            if (appInfo.isEnable() == 1) {
                                mListDisableAppsNew.get(mListDisableAppsNew.indexOf(appInfo)).setEnable(0);
                                commandSu(COMMAND_DISABLE + appInfo.getAppPackageName(), false, appInfo, -1);
                                ((MainActivity) mActivity).getSelection().remove(appInfo);
                                mAppInfoDBController.updateDisableApp(appInfo.getAppPackageName(), 0,
                                        AppInfoDBOpenHelper.TABLE_NAME_APP_INFO);
                                isEnable = true;
                            }
                        } else if (type == 1) { // 启用应用
                            if (appInfo.isEnable() == 0) {
                                mListDisableAppsNew.get(mListDisableAppsNew.indexOf(appInfo)).setEnable(1);
                                commandSu(COMMAND_ENABLE + appInfo.getAppPackageName(), false, appInfo, -1);
                                ((MainActivity) mActivity).getSelection().remove(appInfo);
                                mAppInfoDBController.updateDisableApp(appInfo.getAppPackageName(), 1,
                                        AppInfoDBOpenHelper.TABLE_NAME_APP_INFO);
                            }
                        } else if (type == 2) { // 移除列表
                            if (appInfo.isEnable() == 0) {
                                commandSu(COMMAND_ENABLE + appInfo.getAppPackageName(), false, null, -1);
                                isEnable = true;
                            }
                            ((MainActivity) mActivity).getSelection().remove(appInfo);
                            mListDisableAppsNew.remove(appInfo);
                            mAppInfoDBController.deleteDisableApp(appInfo.getAppPackageName(),
                                    AppInfoDBOpenHelper.TABLE_NAME_APP_INFO);
                        }
                    }
                    if (type == 0 && !isEnable) {
                        mView.getRootSuccess(null, mListDisableApps, mListDisableAppsNew);
                        mListDisableApps = mListDisableAppsNew;
                    }
                    if (type == 2 && !isEnable) {
                        mView.getRootSuccess(null, mListDisableApps, mListDisableAppsNew);
                        mListDisableApps = mListDisableAppsNew;
                    }
                } else {
                    mView.getRootError();
                }
            }
        });
    }

    @Override
    public void uninstallApp(final AppInfo appInfo, final int position) {
        mAppsRepository.commandSu(COMMAND_UNINSTALL + appInfo.getAppPackageName(), new AppsRepository.GetAppsCmdCallback() {
            @Override
            public void onRootAppsLoaded(List<AppInfo> apps) {

            }

            @Override
            public void onRootError() {
                mView.getRootError();
            }

            @Override
            public void onRootSuccess() {
                mAppInfoDBController.deleteDisableApp(appInfo.getAppPackageName(), AppInfoDBOpenHelper.TABLE_NAME_APP_INFO);
                mView.uninstallSuccess(appInfo.getAppName(), position);
            }
        });
    }

    @Override
    public void cancelTask() {
        mAppsRepository.cancelTsk();
    }
}
