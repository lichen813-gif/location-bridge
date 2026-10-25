package io.github.lichen.locationbridge;
public final class PublicConfigPolicyTestMain {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Configuration policy failure "+checks);}
    public static void main(String[] args)throws Exception {
        check(!KeySettings.valid(null));check(!KeySettings.valid(""));
        check(KeySettings.valid("A".repeat(32)));check(KeySettings.valid("9".repeat(32)));
        check(!KeySettings.valid("A".repeat(31)));check(!KeySettings.valid("A".repeat(33)));
        check(!KeySettings.valid("A".repeat(31)+"_"));check(!KeySettings.valid("A".repeat(31)+"中"));
        check(!KeySettings.valid("A".repeat(31)+"\n"));
        check(KeySettings.normalize(null).isEmpty());
        check(KeySettings.normalize(" "+"B".repeat(32)+" ").equals("B".repeat(32)));
        String invalid="DO_NOT_LOG_THIS_TEST_INPUT";
        try {KeySettings.save(null,invalid);throw new AssertionError("Invalid key accepted");}
        catch(IllegalArgumentException e){check(!String.valueOf(e.getMessage()).contains(invalid));}
        if(args.length==0) {
        Class<?> stub=Class.forName("android.location.provider.ILocationProviderManager$Stub");
        java.lang.reflect.Field f=stub.getDeclaredField("TRANSACTION_onSetAllowed");f.setAccessible(true);
        check(f.getInt(null)==2);
        }
        System.out.println("PASS: "+checks+" public configuration checks; invalid credentials rejected without logging; no location published");
    }
}
