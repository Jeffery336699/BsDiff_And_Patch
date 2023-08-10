package com.zhy.bsdiff_and_patch;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.zhy.utils.ApkExtract;
import com.zhy.utils.BsPatch;

import java.io.File;

/**
 * diff 参考: https://blog.csdn.net/lmj623565791/article/details/52761658?spm=1001.2014.3001.5502
 * diff windows 工具参考: https://github.com/welcome112s/bsdiff
 * 命令: 就两个
 * bsdiff old.apk new.apk old-to-new.patch
 * bspatch old.apk new2.apk old-to-new.patch
 *
 * 7.0、8.0适配安装应用: https://juejin.cn/post/6973097798434570254
 */
public class MainActivity extends AppCompatActivity {

    private Button mBtnPatch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mBtnPatch = (Button) findViewById(R.id.id_btn_patch);
        mBtnPatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
                } else {
                    doBspatch();
                }
            }
        });
    }

    private void doBspatch() {
        final File destApk = new File(Environment.getExternalStorageDirectory(), "new2.apk");
        final File orgApk = new File(Environment.getExternalStorageDirectory(), "old.apk");
        final File patch = new File(Environment.getExternalStorageDirectory(), "old-to-new.patch");

        Log.e("hongyang", "patch = " + patch.exists() + " , " + patch.getAbsolutePath());

        String oldApk=ApkExtract.extract(this);

        // TODO: 直接采用旧的apk包+patch包生成的是ok的,证明JNI层的代码没问题.采用上面获取base.apk出了点问题,可能是中间有修改了点源码,问题不大
        oldApk = orgApk.getAbsolutePath();
        BsPatch.bspatch(oldApk,
                destApk.getAbsolutePath(),
                patch.getAbsolutePath());

        Log.e("hongyang", new File(Environment.getExternalStorageDirectory(), "new2.apk").getAbsolutePath() + " , " + destApk.exists());

        if (destApk.exists()){
            // mTargetApk =destApk;
            // ApkExtract.install(this, destApk.getAbsolutePath());
            checkAndroidO();
        }
    }

    private static final int INSTALL_PACKAGES_REQUESTCODE = 10011;
    private static final int GET_UNKNOWN_APP_SOURCES = 10012;

    private void checkAndroidO() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { //系统 Android O及以上版本
            //是否需要处理未知应用来源权限。 true为用户信任安装包安装 false 则需要获取授权
            boolean canRequestPackageInstalls = getPackageManager().canRequestPackageInstalls();
            if (canRequestPackageInstalls) {
                installApk();
            } else {
                //请求安装未知应用来源的权限
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.REQUEST_INSTALL_PACKAGES}, INSTALL_PACKAGES_REQUESTCODE);
            }
        } else {  //直接安装流程
            installApk();
        }
    }

    private void installApk() { //安装程序
        Intent  intentUpdate = new Intent("android.intent.action.VIEW");
        intentUpdate.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {  //对Android N及以上的版本做判断
            Uri apkUriN = FileProvider.getUriForFile(MainActivity.this,getApplicationContext().getPackageName() + ".FileProvider", new File(Environment.getExternalStorageDirectory(), "new2.apk"));
            intentUpdate.addCategory("android.intent.category.DEFAULT");
            intentUpdate.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);   //天假Flag 表示我们需要什么权限
            intentUpdate.setDataAndType(apkUriN, "application/vnd.android.package-archive");
        } else {
            Uri apkUri = Uri.fromFile(new File(Environment.getExternalStorageDirectory(), "app-debug.apk"));
            intentUpdate.setDataAndType(apkUri, "application/vnd.android.package-archive");
        }
        startActivity(intentUpdate);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case INSTALL_PACKAGES_REQUESTCODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {  //如果已经有这个权限 则直接安装 否则跳转到授权界面
                    installApk();
                } else {
                    Uri packageURI = Uri.parse("package:" + getPackageName());   //获取包名，直接跳转到对应App授权界面
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageURI);
                    startActivityForResult(intent, GET_UNKNOWN_APP_SOURCES);
                }
                break;
            case 2:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    doBspatch();
                }
                break;
        }
    }

        //我们还需要在 onActivityResult方法中继续做一些相应的处理，好让授权成功后 返回App可以直接安装
        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            //8.0 以上系统 强更新授权 界面
            switch (requestCode) {
                case GET_UNKNOWN_APP_SOURCES:
                    checkAndroidO();
                    break;
                default:
                    break;
            }

        }


    public void onInstall(View view) {
        checkAndroidO();
    }
}
