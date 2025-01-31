package com.sywyar.midiplayinidv;

public class SongProgressBar {
    private final long totalMillis;
    private final int barLength;
    private boolean stop = false;

    private long currentMillis = 0;

    public SongProgressBar(long totalMillis, int barLength) {
        this.totalMillis = totalMillis;
        this.barLength = barLength;
    }

    public void update() {
        if (currentMillis >= totalMillis && !stop) {
            currentMillis = totalMillis;
            renderProgressBar();
            System.out.println();
            stop = true;
            return;
        }
        currentMillis++;
        renderProgressBar();
    }

    private void renderProgressBar() {
        if (stop) {
            return;
        }

        double progress = (double) currentMillis / totalMillis;
        int filledLength = (int) (progress * barLength);

        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            if (i < filledLength) {
                bar.append("=");
            } else if (i == filledLength) {
                bar.append(">");
            } else {
                bar.append(" ");
            }
        }
        bar.append("]");

        String currentTime = formatTime(currentMillis);
        String totalTime = formatTime(totalMillis);

        String output = String.format("%s %s %.1f%%",
                bar, currentTime + "/" + totalTime, progress * 100);

        System.out.print("\r" + output);
    }

    private String formatTime(long millis) {
        long minutes = millis / (1000 * 60);
        long remainingMillis = millis % (1000 * 60);
        long seconds = remainingMillis / 1000;
        long millisecondPart = remainingMillis % 1000;

        return String.format("%02d:%02d.%03d", minutes, seconds, millisecondPart);
    }

    public void setCurrentMillis(long millis) {
        currentMillis = millis;
        renderProgressBar();
        if (currentMillis >= totalMillis) {
            update();
        }
    }

    public void addCurrentMillis(long millis) {
        currentMillis += millis;
        renderProgressBar();
        if (currentMillis >= totalMillis) {
            update();
        }
    }

    public long getTotalMillis() {
        return totalMillis;
    }

    public long getCurrentMillis() {
        return currentMillis;
    }
}
