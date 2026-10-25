package io.github.lichen.locationbridge;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
/** android.permission.DUMP guards this receiver against ordinary apps. */
public final class TestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        String a=intent.getAction();
        if(!"io.github.lichen.locationbridge.TEST_START".equals(a)
                &&!"io.github.lichen.locationbridge.TEST_STOP".equals(a)) return;
        context.startService(new Intent(context,NetworkLocationService.class)
                .setAction(a.endsWith("TEST_START")?"TEST_START":"TEST_STOP"));
    }
}
