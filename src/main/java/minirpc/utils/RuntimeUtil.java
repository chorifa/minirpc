package minirpc.utils;

public class RuntimeUtil {

    public static final int JDK_VERSION = javaVersion0();

    private static int javaVersion0(){
        String version = System.getProperty("java.specification.version");
        String[] tmps = version.split("\\.");
        if(tmps[0].equals("1"))
            return Integer.parseInt(tmps[1]);
        else return Integer.parseInt(tmps[0]);
    }

}
