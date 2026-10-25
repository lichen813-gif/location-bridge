package io.github.lichen.locationbridge;
import android.os.Parcel;
import android.os.Parcelable;
import java.lang.reflect.Method;

/** Tests wire decoding against the phone's real Parcelable implementation. No provider changes. */
public final class ProtocolTestMain {
    public static void main(String[] args)throws Exception {
        Class<?> type=Class.forName("android.location.provider.ProviderRequest");
        Class<?> builderType=Class.forName("android.location.provider.ProviderRequest$Builder");
        for(long interval:new long[]{0,10_000,300_000}) {
            Object builder=builderType.getConstructor().newInstance();
            builderType.getMethod("setIntervalMillis",long.class).invoke(builder,interval);
            Parcelable request=(Parcelable)builderType.getMethod("build").invoke(builder);
            roundTrip(request,interval);
        }
        roundTrip((Parcelable)type.getField("EMPTY_REQUEST").get(null),Long.MAX_VALUE);
        System.out.println("PASS: 4 Android 17 ProviderRequest parcel round trips");
    }
    private static void roundTrip(Parcelable value,long expected) {
        Parcel parcel=Parcel.obtain();
        try {
            parcel.writeTypedObject(value,0);parcel.setDataPosition(0);
            if(ProviderWire.readRequestInterval(parcel)!=expected)throw new AssertionError("Incorrect interval");
        } finally { parcel.recycle(); }
    }
}
