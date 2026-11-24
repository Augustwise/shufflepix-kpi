package com.shufflepix.client;

import java.util.Arrays;

/**
 * Допоміжна точка входу для запуску клієнта з готовим тестовим проєктом.
 */
public final class DemoLauncher {
    private DemoLauncher() {
    }

    public static void main(String[] args) {
        String[] demoArgs = Arrays.copyOf(args, args.length + 1);
        demoArgs[args.length] = ShufflePixApp.DEMO_ARG;
        ShufflePixApp.main(demoArgs);
    }
}
