package com.sywyar.midiplayinidv;

import com.sywyar.keyboard.KeyBoardDLL;
import com.sywyar.keyboard.keyboardenum.KeyCodeEnum;
import com.sywyar.keyboard.keyboardenum.KeyTypeEnum;

import javax.sound.midi.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class MidiPlayInIdv {
    //static final String[] notes = {"A", "A#", "B", "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#"};
    //static final int[] notesNumber = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
    static final int[] fall = {1, 4, 6, 9, 11};

    public static void main(String[] args) throws InvalidMidiDataException, IOException, InterruptedException {
        if (args.length < 1 || args[0].isEmpty()) {
            System.out.println("缺少参数，请在命令行末尾添加MIDI文件路径，例如:\"java -jar MidiPlayInIdv.jar C:/qby.mid\"");
            return;
        }
        String midiFilePath = args[0];
        File file = new File(midiFilePath);
        List<File> midiFiles = new ArrayList<>();

        if (file.isFile()) {
            midiFiles.add(file);
        } else {
            File[] files = file.listFiles();
            if (files == null || files.length == 0) {
                System.out.println("目标目录没有文件，再见!");
                return;
            }
            for (File testFile : files) {
                if (isMidiFile(testFile)) {
                    midiFiles.add(testFile);
                }
            }
        }

        System.out.println("正在解析所有midi文件，请稍等");
        Queue<MultiValueMap<Long, MidiPlayMessage>> multiValueMaps = new LinkedList<>();
        ProgressBar parsingProgressBar = new ProgressBar("解析进度", midiFiles.size());
        for (File midiFile : midiFiles) {
            MultiValueMap<Long, MidiPlayMessage> midiPlayMessages = parsingMidiFile(midiFile);
            multiValueMaps.add(midiPlayMessages);

            parsingProgressBar.update();
        }

        parsingProgressBar.close();

        int num = 0;
        System.out.println("共" + multiValueMaps.size() + "首歌,播放列表如下：");
        for (MultiValueMap<Long, MidiPlayMessage> multiValueMap : multiValueMaps) {
            System.out.println(++num + "." + multiValueMap.getFileName());
        }

        System.out.println();

        for (MultiValueMap<Long, MidiPlayMessage> multiValueMap : multiValueMaps) {
            SongProgressBar progressBar = new SongProgressBar(multiValueMap.getTotalSeconds(), 40);
            System.out.println("准备弹奏：" + multiValueMap.getFileName());
            Thread.sleep(1000);
            playMidiFile(multiValueMap, progressBar);
        }
    }

    public static boolean isMidiFile(File file) {
        if (file == null || file.isDirectory()) {
            return false;
        }
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex >= fileName.length() - 1) {
            return false;
        }
        String extension = fileName.substring(dotIndex + 1).toLowerCase();
        return extension.equals("mid") || extension.equals("midi");
    }

    private static MultiValueMap<Long, MidiPlayMessage> parsingMidiFile(File file) throws InvalidMidiDataException, IOException {
        Sequence sequence = MidiSystem.getSequence(file);

        int resolution = sequence.getResolution();
        int tempo = 500000;

        List<TempoEvent> tempoEvents = new ArrayList<>();
        long maxTick = 0;

        MultiValueMap<Long, MidiPlayMessage> midiPlayMessages = new MultiValueMap<>(resolution, file.getName());

        boolean sustainOn = false;
        List<Integer> sustainedNotes = new ArrayList<>();
        HashSet<Integer> ignoreEvents = new HashSet<>();

        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage message = event.getMessage();

                if (event.getTick() > maxTick) {
                    maxTick = event.getTick();
                }

                if (message instanceof MetaMessage metaMessage) {
                    if (metaMessage.getType() == 0x51) {
                        byte[] data = metaMessage.getData();
                        tempo = ((data[0] & 0xFF) << 16) | ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
                        tempoEvents.add(new TempoEvent(event.getTick(), tempo));
                    }
                }

                if (message instanceof ShortMessage sm) {
                    int command = sm.getCommand();
                    int key = sm.getData1();
                    int velocity = sm.getData2();
                    long tick = event.getTick();

                    if (command == ShortMessage.NOTE_ON && velocity > 0) {
                        key = findMidiNumber(key);
                        MidiPlayMessage midiPlayMessage = new MidiPlayMessage(findKeyCode(key), KeyTypeEnum.down);
                        midiPlayMessages.put(tick, midiPlayMessage, tempo);

                        if (sustainOn) {
                            sustainedNotes.add(key);
                        }
                    } else if (command == ShortMessage.NOTE_OFF || (command == ShortMessage.NOTE_ON && velocity == 0)) {
                        key = findMidiNumber(key);
                        if (sustainOn) {
                            sustainedNotes.add(key);
                        } else {
                            MidiPlayMessage midiPlayMessage = new MidiPlayMessage(findKeyCode(key), KeyTypeEnum.up);
                            midiPlayMessages.put(tick, midiPlayMessage, tempo);
                        }
                    } else if (command == ShortMessage.CONTROL_CHANGE && sm.getData1() == 64) {
                        if (sm.getData2() >= 64) {
                            sustainOn = true;
                        } else {
                            sustainOn = false;

                            for (int sustainedKey : sustainedNotes) {
                                MidiPlayMessage midiPlayMessage = new MidiPlayMessage(findKeyCode(sustainedKey), KeyTypeEnum.up);
                                midiPlayMessages.put(tick, midiPlayMessage, tempo);
                            }
                            sustainedNotes.clear();
                        }
                    } else {
                        ignoreEvents.add(command);
                    }
                }
            }
        }

        if (tempoEvents.isEmpty()) {
            tempoEvents.add(new TempoEvent(0, 500000));
        }

        tempoEvents.sort(Comparator.comparingLong(TempoEvent::tick));

        long currentTick = 0;
        double totalSeconds = 0;
        int currentTempo = tempoEvents.getFirst().tempo();

        for (TempoEvent event : tempoEvents) {
            long deltaTick = event.tick() - currentTick;
            if (deltaTick > 0) {
                double deltaSeconds = (deltaTick * currentTempo) / (resolution * 1_000_000.0);
                totalSeconds += deltaSeconds;
                currentTick = event.tick();
            }
            currentTempo = event.tempo();
        }

        if (maxTick > currentTick) {
            long deltaTick = maxTick - currentTick;
            double deltaSeconds = (deltaTick * currentTempo) / (resolution * 1_000_000.0);
            totalSeconds += deltaSeconds;
        }
        midiPlayMessages.setTotalSeconds((int) totalSeconds);

        //if (!ignoreEvents.isEmpty()) System.out.println("忽略的MIDI事件=" + ignoreEvents);
        midiPlayMessages.setIgnoreEvents(ignoreEvents);

        return midiPlayMessages;
    }

    private static void playMidiFile(MultiValueMap<Long, MidiPlayMessage> midiPlayMessages, SongProgressBar progressBar) throws InterruptedException {
        KeyBoardDLL keyBoard = new KeyBoardDLL();
        long ppq = midiPlayMessages.getResolution();
        long lastTick = 0;
        Set<Long> sortedKeys = new TreeSet<>(midiPlayMessages.keySet());
        for (Long l : sortedKeys) {
            List<MidiPlayMessage> list = midiPlayMessages.getValues(l);

            long tickDiff = Math.abs(l - lastTick);
            double microsecondsPerTick = (midiPlayMessages.getTempo(l) / (double) ppq);
            long timeInMillis = (long) ((tickDiff * microsecondsPerTick) / 1000);
            int part = (int) (timeInMillis / 300);
            long remainingTime = timeInMillis % 300;
            for (int i = 0; i < part; i++) {
                Thread.sleep(300);
                progressBar.addCurrentMillis(300);
            }
            Thread.sleep(remainingTime);
            progressBar.addCurrentMillis(remainingTime);

            lastTick = l;
            for (MidiPlayMessage midiPlayMessage : list) {
                keyBoard.sendKeyboardMessage(midiPlayMessage.keyCode(), midiPlayMessage.keyType());
            }
        }

        if (progressBar.getCurrentMillis() < progressBar.getTotalMillis()) {
            progressBar.setCurrentMillis(progressBar.getTotalMillis());
        }
    }

    public static KeyCodeEnum findKeyCode(int key) {
        for (MidiNumber value : MidiNumber.values()) {
            if (value.getMidiNumber() == key) {
                return KeyCodeEnum.valueOf(value.name());
            }
        }
        return KeyCodeEnum.noting;
    }

    public static int findMidiNumber(int key) {
        int octave = (key - 9) / 12;
        int noteIndex = (key - 9) % 12;

        int finalNoteIndex = noteIndex;
        boolean exists = Arrays.stream(fall)
                .anyMatch(num -> num == finalNoteIndex);

        if (exists) {
            noteIndex--;
        }

        if (octave < 4) {
            octave = 4;
        } else if (octave > 6) {
            octave = 6;
        }

        return octave * 12 + noteIndex + 9;
    }
}
