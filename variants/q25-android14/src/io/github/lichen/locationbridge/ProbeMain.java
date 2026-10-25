package io.github.lichen.locationbridge;

import org.json.JSONObject;

/** Runs in a separate app_process for read-only inspection, never in system_server. */
public final class ProbeMain {
    public static void main(String[] args) {
        System.out.println(new JSONObject(SystemProbe.inspect(ProbeMain.class.getClassLoader())));
    }
}
