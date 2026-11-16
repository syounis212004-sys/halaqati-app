package com.mohammedsamir.halaqati.updater;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@CapacitorPlugin(name = "HalaqatiUpdater")
public class HalaqatiUpdaterPlugin extends Plugin {
    private long versionCode(PackageInfo p) { return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode; }

    @com.getcapacitor.PluginMethod
    public void getVersion(PluginCall call) {
        try {
            PackageManager pm=getContext().getPackageManager();
            PackageInfo pi=pm.getPackageInfo(getContext().getPackageName(),0);
            JSObject out=new JSObject();
            out.put("versionName",pi.versionName);
            out.put("versionCode",versionCode(pi));
            out.put("packageName",getContext().getPackageName());
            out.put("canInstallPackages",Build.VERSION.SDK_INT<26 || pm.canRequestPackageInstalls());
            call.resolve(out);
        } catch(Exception e){ call.reject(e.getMessage(),e); }
    }

    @com.getcapacitor.PluginMethod
    public void openInstallPermissionSettings(PluginCall call) {
        try {
            if(Build.VERSION.SDK_INT>=26){
                Intent i=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:"+getContext().getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);getContext().startActivity(i);
            }
            call.resolve();
        } catch(Exception e){ call.reject(e.getMessage(),e); }
    }

    @com.getcapacitor.PluginMethod
    public void downloadAndInstall(PluginCall call) {
        String url=call.getString("url");
        String expectedPackage=call.getString("expectedPackage",getContext().getPackageName());
        Integer expectedVersion=call.getInt("expectedVersionCode");
        if(url==null||url.trim().isEmpty()){call.reject("Missing update URL");return;}
        if(Build.VERSION.SDK_INT>=26 && !getContext().getPackageManager().canRequestPackageInstalls()){
            JSObject o=new JSObject();o.put("permissionRequired",true);call.resolve(o);return;
        }
        new Thread(() -> {
            HttpURLConnection c=null;
            try {
                File f=new File(getContext().getCacheDir(),"halaqati-update.apk");
                c=(HttpURLConnection)new URL(url).openConnection();
                c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(true);c.connect();
                if(c.getResponseCode()<200||c.getResponseCode()>=300)throw new Exception("Download HTTP "+c.getResponseCode());
                long total=c.getContentLengthLong(),done=0;byte[] buf=new byte[32768];int n,last=-1;
                try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(f)){
                    while((n=in.read(buf))>0){out.write(buf,0,n);done+=n;if(total>0){int pct=(int)Math.min(100,(done*100/total));if(pct!=last){last=pct;JSObject ev=new JSObject();ev.put("percent",pct);notifyListeners("downloadProgress",ev);}}}
                }
                PackageManager pm=getContext().getPackageManager();
                PackageInfo apk=pm.getPackageArchiveInfo(f.getAbsolutePath(),0);
                if(apk==null)throw new Exception("ملف APK غير صالح");
                if(!expectedPackage.equals(apk.packageName))throw new Exception("اسم حزمة التحديث لا يطابق تطبيق حلقتي");
                if(expectedVersion!=null && versionCode(apk)<expectedVersion.longValue())throw new Exception("إصدار APK أقل من الإصدار المتوقع");
                Uri uri=FileProvider.getUriForFile(getContext(),getContext().getPackageName()+".halaqatiupdater.fileprovider",f);
                Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(uri,"application/vnd.android.package-archive");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
                JSObject res=new JSObject();res.put("started",true);res.put("versionCode",versionCode(apk));call.resolve(res);
            } catch(Exception e){call.reject(e.getMessage(),e);} finally {if(c!=null)c.disconnect();}
        }).start();
    }
}
