package io.github.lichen.locationbridge;

import android.Manifest;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedModule;

/** Narrow provider selection override. Keeps the original supplier and GMS installed. */
public final class BridgeModule extends XposedModule {
    private static final String TAG="LocationBridgeModule";
    private static final String PACKAGE="io.github.lichen.locationbridge";
    private static final String SERVICE=PACKAGE+".NetworkLocationService";
    private static final String CERT=BuildIdentity.CERT_SHA256;
    public static final String ENABLE_SETTING="lichen_location_bridge_enabled";
    private boolean systemServer;
    private String processName="";
    private final AtomicBoolean gmsAttempted=new AtomicBoolean();
    private final AtomicBoolean selected=new AtomicBoolean(),failureReported=new AtomicBoolean();

    private static boolean supportedRuntime() {
        return Build.VERSION.SDK_INT==34 && "Q25".equals(Build.DEVICE)
                && "Xelex/Xelex10_Ultra/Xelex10_Ultra:14/20240427/UP1v:user/release-keys".equals(Build.FINGERPRINT);
    }

    @Override public void onModuleLoaded(ModuleLoadedParam p) {
        systemServer=p.isSystemServer();
        processName=p.getProcessName();
        log(Log.INFO,TAG,"Loaded; API="+getApiVersion()+"; system_server="+systemServer);
    }
    @Override public void onPackageReady(PackageReadyParam p) {
        if(!systemServer&&"com.google.android.gms".equals(p.getPackageName())
                &&"com.google.android.gms".equals(processName)&&android.os.Process.myUid()/100000==0
                &&supportedRuntime()&&getApiVersion()>=102
                &&gmsAttempted.compareAndSet(false,true))
            new GmsNlpBridge(this).install(p.getClassLoader(),p.getApplicationInfo().sourceDir);
    }
    @Override public void onSystemServerStarting(SystemServerStartingParam p) {
        if(!systemServer||!supportedRuntime()
                ||getApiVersion()<102||(getFrameworkProperties()&PROP_CAP_SYSTEM)==0) return;
        try {
            Class<?> supplier=Class.forName(SystemProbe.SUPPLIER,false,p.getClassLoader());
            Class<?> info=Class.forName(SystemProbe.SUPPLIER+"$BoundServiceInfo",false,p.getClassLoader());
            Field context=supplier.getDeclaredField("mContext"),intent=supplier.getDeclaredField("mIntent");
            context.setAccessible(true);intent.setAccessible(true);
            Constructor<?> constructor=info.getDeclaredConstructor(String.class,ResolveInfo.class);
            Method user=info.getMethod("getUserId");
            Method getInfo=supplier.getDeclaredMethod("getServiceInfo");
            hook(getInfo).setId("location-bridge-network-selection")
                    .setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain->{
                Object original=chain.proceed();
                try {
                    Intent filter=(Intent)intent.get(chain.getThisObject());
                    if(original==null||!SystemProbe.NETWORK_ACTION.equals(filter.getAction())
                            ||((Integer)user.invoke(original))!=0) return original;
                    Context c=(Context)context.get(chain.getThisObject());
                    if(Settings.Global.getInt(c.getContentResolver(),ENABLE_SETTING,0)!=1
                            ||!c.getSystemService(UserManager.class).isUserUnlocked()) return original;
                    ResolveInfo candidate=verifiedCandidate(c);
                    if(candidate==null)return original;
                    Object alternative=getInvoker(constructor).newInstance(SystemProbe.NETWORK_ACTION,candidate);
                    if(selected.compareAndSet(false,true)) log(Log.INFO,TAG,"Selected signed Baidu backend for user 0 network provider");
                    return alternative;
                } catch(Exception|LinkageError e) {
                    if(failureReported.compareAndSet(false,true)) log(Log.WARN,TAG,"Selection unavailable; original supplier retained",e);
                    return original;
                }
            });
            log(Log.INFO,TAG,"Network selection hook installed; switch defaults off");
        } catch(ReflectiveOperationException|LinkageError e) {
            log(Log.WARN,TAG,"Required Q25 Android 14 signatures unavailable; no replacement",e);
        }
    }
    private ResolveInfo verifiedCandidate(Context c) throws Exception {
        PackageManager pm=c.getPackageManager();
        if(pm.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION,PACKAGE)!=PackageManager.PERMISSION_GRANTED
                ||pm.checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION,PACKAGE)!=PackageManager.PERMISSION_GRANTED)return null;
        PackageInfo installed=pm.getPackageInfo(PACKAGE,PackageManager.GET_SIGNING_CERTIFICATES);
        if(installed.getLongVersionCode()<2||installed.signingInfo==null
                ||installed.signingInfo.hasMultipleSigners())return null;
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(installed.signingInfo.getApkContentsSigners()[0].toByteArray());
        StringBuilder hex=new StringBuilder();
        for(byte b:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        if(!CERT.contentEquals(hex))return null;
        Intent query=new Intent(SystemProbe.NETWORK_ACTION).setComponent(new ComponentName(PACKAGE,SERVICE));
        ResolveInfo result=pm.resolveService(query,PackageManager.GET_META_DATA
                |PackageManager.MATCH_DIRECT_BOOT_AWARE|PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
        if(result==null||result.serviceInfo==null)return null;
        ServiceInfo s=result.serviceInfo;
        if(!PACKAGE.equals(s.packageName)||!SERVICE.equals(s.name)||!s.enabled||!s.applicationInfo.enabled
                ||s.exported||s.metaData==null||s.metaData.getInt("serviceVersion",0)<2)return null;
        return result;
    }
}
