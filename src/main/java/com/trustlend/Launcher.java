package com.trustlend;

/**
 * Plain main class used by the fat jar. When the class with main() extends Application,
 * the JDK launcher insists on JavaFX being on the module path; going through a
 * non-Application class avoids the "JavaFX runtime components are missing" error.
 */
public final class Launcher {

    private Launcher() { }

    public static void main(String[] args) {
        TrustLendApp.main(args);
    }
}
