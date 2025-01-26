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
        Sequence sequence = MidiSystem.getSequence(new File(midiFilePath));
        int ppq = sequence.getResolution();
        double bpm = 120;

        MultiValueMap<Long, MidiPlayMessage> midiPlayMessages = new MultiValueMap<>();
        boolean sustainOn = false;
        List<Integer> sustainedNotes = new ArrayList<>();

        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage message = event.getMessage();

                if (message instanceof MetaMessage metaMessage) {
                    if (metaMessage.getType() == 0x51) {
                        byte[] data = metaMessage.getData();
                        int tempo = ((data[0] & 0xFF) << 16) | ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);

                        bpm = 60000000.0 / tempo;
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
                        midiPlayMessages.put(tick, midiPlayMessage);

                        if (sustainOn) {
                            sustainedNotes.add(key);
                        }
                    } else if (command == ShortMessage.NOTE_OFF || (command == ShortMessage.NOTE_ON && velocity == 0)) {
                        key = findMidiNumber(key);
                        if (sustainOn) {
                            sustainedNotes.add(key);
                        } else {
                            MidiPlayMessage midiPlayMessage = new MidiPlayMessage(findKeyCode(key), KeyTypeEnum.up);
                            midiPlayMessages.put(tick, midiPlayMessage);
                        }
                    } else if (command == ShortMessage.CONTROL_CHANGE && sm.getData1() == 64) {
                        if (sm.getData2() >= 64) {
                            sustainOn = true;
                        } else {
                            sustainOn = false;

                            for (int sustainedKey : sustainedNotes) {
                                MidiPlayMessage midiPlayMessage = new MidiPlayMessage(findKeyCode(sustainedKey), KeyTypeEnum.up);
                                midiPlayMessages.put(tick, midiPlayMessage);
                            }
                            sustainedNotes.clear();
                        }
                    }
                }
            }
        }

        System.out.println("解析完成，3秒后弹奏");
        Thread.sleep(3000);

        KeyBoardDLL keyBoard = new KeyBoardDLL();
        long lastTick = 0;
        Set<Long> sortedKeys = new TreeSet<>(midiPlayMessages.keySet());
        for (Long l : sortedKeys) {
            List<MidiPlayMessage> list = midiPlayMessages.get(l);

            long tickDiff = Math.abs(l - lastTick);
            long timeInMillis = (long) ((tickDiff * 60000.0) / (bpm * ppq));
            Thread.sleep(timeInMillis);

            lastTick = l;
            for (MidiPlayMessage midiPlayMessage : list) {
                keyBoard.sendKeyboardMessage(midiPlayMessage.keyCode(), midiPlayMessage.keyType());
            }
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
