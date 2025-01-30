package com.sywyar.midiplayinidv;

public class ProgressBar {
    private final String text;
    private final int total;
    private int num = 0;

    public ProgressBar(String text, int total) {
        this.text = text;
        this.total = total;

        update();
    }

    public void update() {
        String nowText = text + ":" + num + "/" + total;
        System.out.printf("\r" + nowText);
        num++;
    }

    public void close() {
        System.out.println();
    }
}
