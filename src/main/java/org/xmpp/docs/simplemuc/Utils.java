package org.xmpp.docs.simplemuc;

public class Utils {

    static void sleep(long interval) {
        try {
            Thread.sleep(interval);
        } catch (InterruptedException e) {

        }
    }
}
