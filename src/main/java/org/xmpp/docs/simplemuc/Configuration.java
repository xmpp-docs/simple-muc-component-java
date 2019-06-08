package org.xmpp.docs.simplemuc;

public class Configuration {

    private static final String COMPONENT_NAME = "muc.gpn.siacs.eu";
    private static final String SHARED_SECRET = "eiyiedieth6Ahdae7oci";
    private static final String HOSTNAME = "localhost";
    private static final int PORT = 5347;

    private static final boolean DEBUG = true;

    public static String getComponentName() {
        return COMPONENT_NAME;
    }

    public static String getSharedSecret() {
        return SHARED_SECRET;
    }

    public static String getHostname() {
        return HOSTNAME;
    }

    public static int getPort() {
        return PORT;
    }

    public static boolean isDebug() {
        return DEBUG;
    }
}
