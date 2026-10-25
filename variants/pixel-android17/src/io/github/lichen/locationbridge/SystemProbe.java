package io.github.lichen.locationbridge;

import android.content.Context;
import android.content.res.Resources;
import android.location.Location;
import android.os.Build;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reflects on class definitions only. No services are created or rebound. */
public final class SystemProbe {
    public static final String SUPPLIER =
            "com.android.server.servicewatcher.CurrentUserServiceSupplier";
    public static final String NETWORK_ACTION =
            "com.android.location.service.v3.NetworkLocationProvider";

    private SystemProbe() {}

    public static Method findNetworkFactory(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> supplier = Class.forName(SUPPLIER, false, loader);
        Method method = supplier.getDeclaredMethod("createFromConfig", Context.class,
                String.class, int.class, int.class);
        if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != supplier) {
            throw new NoSuchMethodException("Unexpected provider selection factory");
        }
        return method;
    }

    public static Map<String, String> inspect(ClassLoader loader) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("sdk", Integer.toString(Build.VERSION.SDK_INT));
        result.put("build", Build.ID);
        result.put("mode", "read_only_class_inventory");
        result.put("locations_read_or_written", "0");
        check(result, "network_selection_factory", () -> findNetworkFactory(loader).toString());
        check(result, "supplier_constructor", () -> {
            Constructor<?> c = Class.forName(SUPPLIER, false, loader).getDeclaredConstructor(
                    Context.class, String.class, String.class, String.class, String.class,
                    boolean.class);
            return c.toString();
        });
        check(result, "network_proxy_factory", () -> Class.forName(
                "com.android.server.location.provider.proxy.ProxyLocationProvider", false, loader)
                .getDeclaredMethod("create", Context.class, String.class, String.class,
                        int.class, int.class).toString());
        check(result, "provider_base_constructor", () -> Class.forName(
                "android.location.provider.LocationProviderBase", false, loader)
                .getDeclaredConstructor(Context.class, String.class,
                        Class.forName("android.location.provider.ProviderProperties", false, loader))
                .toString());
        check(result, "provider_report_location", () -> Class.forName(
                "android.location.provider.LocationProviderBase", false, loader)
                .getDeclaredMethod("reportLocation", Location.class).toString());
        check(result, "provider_request_binder", () -> Class.forName(
                "android.location.provider.ILocationProvider", false, loader)
                .getDeclaredMethod("setRequest", Class.forName(
                        "android.location.provider.ProviderRequest", false, loader)).toString());
        check(result, "network_overlay_enabled_resource", () -> {
            Resources r = Resources.getSystem();
            int id = r.getIdentifier("config_enableNetworkLocationOverlay", "bool", "android");
            return id == 0 ? "resource_absent" : Boolean.toString(r.getBoolean(id));
        });
        check(result, "network_package_resource_only", () -> {
            Resources r = Resources.getSystem();
            int id = r.getIdentifier("config_networkLocationProviderPackageName", "string", "android");
            return id == 0 ? "resource_absent" : r.getString(id)
                    + " (resource only, not the current service binding)";
        });
        result.put("vector_injection", "not_proven_by_this_process");
        result.put("google_fused_location", "not_tested");
        result.put("domestic_backend", "not_integrated_key_required");
        return result;
    }

    private interface ReadOnlyCheck { String run() throws Exception; }

    private static void check(Map<String, String> result, String name, ReadOnlyCheck check) {
        try {
            result.put(name, "MATCH: " + check.run());
        } catch (Exception | LinkageError e) {
            result.put(name, "UNAVAILABLE: " + e.getClass().getSimpleName());
        }
    }
}
