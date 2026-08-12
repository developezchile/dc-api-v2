package org.doscolas;

/** Printed once at boot, ahead of any log output — same idea as Spring Boot's {@code banner.txt}. */
final class Banner {

    private Banner() {
    }

    static final String TEXT = """
             ____                      _
            |  _ \\  ___  ___  ___ ___ | | __ _ ___
            | | | |/ _ \\/ __|/ __/ _ \\| |/ _` / __|
            | |_| | (_) \\__ \\ (_| (_) | | (_| \\__ \\
            |____/ \\___/|___/\\___\\___/|_|\\__,_|___/
                                    api-v2
            """;

    static void print() {
        System.out.println(TEXT);
    }
}
