package io.github.lichen.locationbridge;

import java.util.*;

/** Explicit reviewed layouts; unknown APKs may reuse a layout only with identical core classes. */
final class GmsCompatibility {
    record Profile(String version,String helper,String dispatcher,String receiver,String engine,
                   String selector,String flag,Map<String,String> fingerprints) {}
    static final Profile[] PROFILES={
        new Profile("26.41.63", "dsyl", "hrgx", "dsyj", "hpel", "kakb", "p", Map.of(
                "com.google.android.location.internal.server.NlpReport", "7808c1746258b28e8905b376d595b0814f1c8eaa4694917d943c5cb62fb7a585",
                "dsyj", "8c5eaad27c9c119264ff4fdf26314c5d515b48ba269c9d35964d0ca81ad83b53",
                "dsyl", "e5d0ae6bdfcb564d826695eced87e86dd91b653288e37acb6b47414553ad8ee5",
                "hpel", "ff30d3504bb1d20571a7c9300ed3f7b3ff45583f3e1ca2a1d1a570693a052f37",
                "hrgx", "7a2f8eef418acfa5c9e57113a3876789d547d3f027dc01b5d2a16d975068751a",
                "kakb", "d045340ee078b0e7bf3323b517608692e33c3d59ace9f2057cb1f99668203ab4")),
        new Profile("26.42.31", "dtxr", "huct", "dtxp", "hsal", "kdco", "o", Map.of(
                "com.google.android.location.internal.server.NlpReport", "944445f1a8e4262c61a6c637512d0f8cc36646ff314bdb7c716b9793b766bd74",
                "dtxp", "6b4b457dcdd7fd3778cc17b4d58427d2b3baeaf9cb4ea5ec60074910f703c9a8",
                "dtxr", "fb2c54e8b0e280e856d7507c98c0ac902ae4a1dfe3c030f9e6093e6bc561104f",
                "hsal", "e9d499e3ce1fc6f54c8a78cb6e149a227e807a8e6cb7a85bbf889409bdd46d70",
                "huct", "b1523abe27137f45b7ba2106659ec85327227de819423f740ac3071e2f2444e2",
                "kdco", "11398ddb16e039ecb6e793780618a7fdd1f0b46a0a0cf4059e9f7ceffe644cac"))
    };
    static Profile verify(String apk,ClassLoader loader) throws Exception {
        for(Profile p:PROFILES) {
            try {Class.forName(p.helper(),false,loader);}
            catch(ClassNotFoundException e){continue;}
            Map<String,String> actual=DexContract.read(apk,p.fingerprints().keySet());
            if(p.fingerprints().equals(actual))return p;
        }
        throw new IllegalStateException("unreviewed location core");
    }
}
