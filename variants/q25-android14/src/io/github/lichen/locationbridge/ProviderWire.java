package io.github.lichen.locationbridge;
import android.os.Parcel;
import android.os.WorkSource;

/** Android 14 Q25 layout, subject to on-device round trips; originally Android 17 AOSP ProviderRequest layout. No hidden API calls. */
public final class ProviderWire {
    public static final String PROVIDER="android.location.provider.ILocationProvider";
    public static final String MANAGER="android.location.provider.ILocationProviderManager";
    private ProviderWire() {}
    public static long readRequestInterval(Parcel p) {
        if(p.readInt()==0) throw new IllegalArgumentException("Null request");
        long interval=p.readLong();
        if(interval!=Long.MAX_VALUE) {
            if(interval<0) throw new IllegalArgumentException("Negative interval");
            p.readInt();p.readLong(); // quality and maximum batching delay
            p.readBoolean();p.readBoolean();p.readBoolean(); // power, ADAS, bypass hints
            p.readTypedObject(WorkSource.CREATOR);
        }
        p.enforceNoDataAvail();
        return interval;
    }
}
