package com.sywyar.midiplayinidv;

import com.sywyar.keyboard.KeyBoardDLL;
import com.sywyar.keyboard.keyboardenum.KeyCodeEnum;
import com.sywyar.keyboard.keyboardenum.KeyTypeEnum;
import org.mozilla.universalchardet.UniversalDetector;

import javax.sound.midi.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MidiPlayInIdv {
    private static final int[] AVAILABLE_NOTES = {
            // 下行组（低音区）
            48, 50, 52, 53, 55, 57, 59,  // z x c v b n m
            // 中行组（中央C组）
            60, 62, 64, 65, 67, 69, 71,  // a s d f g h j
            // 上行组（高音区）
            72, 74, 76, 77, 79, 81, 83   // q w e r t y u
    };
    private static final int[] AVAILABLE_NOTES_WITH_SHARPS = {
            49, 51, 54, 56, 58,  // 低音区黑键
            61, 63, 66, 68, 70,  // 中音区黑键
            73, 75, 78, 80, 82   // 高音区黑键
    };

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
            String name = multiValueMap.getFileName() + (multiValueMap.getSongName().isEmpty() ? "" : ("(" + multiValueMap.getSongName() + ")"));
            System.out.println(++num + "." + name);
        }

        System.out.println();

        for (MultiValueMap<Long, MidiPlayMessage> multiValueMap : multiValueMaps) {
            SongProgressBar progressBar = new SongProgressBar(multiValueMap.getTotalMillis(), 40);
            String name = multiValueMap.getFileName() + (multiValueMap.getSongName().isEmpty() ? "" : ("(" + multiValueMap.getSongName() + ")"));
            System.out.println("准备弹奏：" + name);
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
                    byte[] data = metaMessage.getData();
                    switch (metaMessage.getType()) {
                        case 0x51:
                            tempo = ((data[0] & 0xFF) << 16) | ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
                            tempoEvents.add(new TempoEvent(event.getTick(), tempo));
                            break;
                        case 0x03:
                            if (midiPlayMessages.getSongName().isEmpty()) {
                                String charsetName = detectCharset(data);
                                Charset charset = StandardCharsets.UTF_8;
                                try {
                                    if (charsetName != null) {
                                        charset = Charset.forName(charsetName);
                                    }
                                } catch (Exception ignored) {
                                }

                                String songName = new String(data, charset);
                                if (isGarbled(songName)) {
                                    songName = tryCommonCharsets(data);
                                }
                                midiPlayMessages.setSongName(songName.trim());
                            }
                            break;
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

        long totalMillis = getTotalMillis(tempoEvents, resolution, maxTick);
        midiPlayMessages.setTotalMillis(totalMillis);
        midiPlayMessages.setTempoEvents(tempoEvents);

        //if (!ignoreEvents.isEmpty()) System.out.println("忽略的MIDI事件=" + ignoreEvents);
        midiPlayMessages.setIgnoreEvents(ignoreEvents);

        return midiPlayMessages;
    }

    private static String detectCharset(byte[] data) {
        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData(data, 0, data.length);
        detector.dataEnd();
        String encoding = detector.getDetectedCharset();
        detector.reset();
        return encoding;
    }

    private static String tryCommonCharsets(byte[] data) {
        List<Charset> candidates = Arrays.asList(
                StandardCharsets.UTF_8,
                StandardCharsets.UTF_16,
                StandardCharsets.UTF_16BE,
                StandardCharsets.UTF_16LE,

                // 简体中文
                Charset.forName("GBK"),          // 中国大陆标准
                Charset.forName("GB2312"),       // 旧版中文编码
                Charset.forName("GB18030"),      // 最新国标扩展

                // 繁体中文
                Charset.forName("Big5"),         // 台湾/香港常用
                Charset.forName("Big5-HKSCS"),   // 香港扩展

                // 日文
                Charset.forName("Shift_JIS"),    // 最广泛使用的日文编码
                Charset.forName("Windows-31J"),  // Shift_JIS 扩展（Windows版）
                Charset.forName("EUC-JP"),       // Unix系统常用
                Charset.forName("ISO-2022-JP"),  // 邮件/网络协议

                // 韩文
                Charset.forName("EUC-KR"),       // 韩文标准
                Charset.forName("ISO-2022-KR"),  // 邮件/网络协议

                // 其他地区
                StandardCharsets.ISO_8859_1,   // 西欧语言（常见错误回退）
                Charset.forName("Windows-1252"), // 西欧扩展
                Charset.forName("TIS-620")       // 泰语（兜底东南亚语言）
        );

        for (Charset charset : candidates) {
            String text = new String(data, charset);
            if (!isGarbled(text)) {
                return text;
            }
        }
        return new String(data, StandardCharsets.UTF_8); // 默认回退
    }

    private static boolean isGarbled(String text) {
        int printable = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x20 && c <= 0x7E || Character.isIdeographic(c)) {
                printable++;
            }
        }
        return (printable * 1.0 / text.length()) < 0.5;
    }

    private static long getTotalMillis(List<TempoEvent> tempoEvents, int resolution, long maxTick) {
        long currentTick = 0;
        long totalMillis = 0;
        int currentTempo = tempoEvents.isEmpty() ? 500000 : tempoEvents.getFirst().tempo();

        for (TempoEvent event : tempoEvents) {
            long deltaTick = event.tick() - currentTick;
            if (deltaTick > 0) {
                long deltaMillis = (deltaTick * (long) currentTempo) / (resolution * 1000L);
                totalMillis += deltaMillis;
                currentTick = event.tick();
            }
            currentTempo = event.tempo();
        }

        if (maxTick > currentTick) {
            long deltaTick = maxTick - currentTick;
            long deltaMillis = (deltaTick * (long) currentTempo) / (resolution * 1000L);
            totalMillis += deltaMillis;
        }

        return totalMillis;
    }

    private static void playMidiFile(MultiValueMap<Long, MidiPlayMessage> midiPlayMessages, SongProgressBar progressBar) throws InterruptedException {
        KeyBoardDLL keyBoard = new KeyBoardDLL();
        long ppq = midiPlayMessages.getResolution();

        List<TempoEvent> tempoEvents = midiPlayMessages.getTempoEvents();
        Set<Long> sortedKeys = new TreeSet<>(midiPlayMessages.keySet());

        int currentTempoIndex = 0;
        int currentTempo = tempoEvents.isEmpty() ? 500000 : tempoEvents.getFirst().tempo();
        long lastTick = 0;

        for (Long currentTick : sortedKeys) {
            while (currentTempoIndex < tempoEvents.size()) {
                TempoEvent nextTempo = tempoEvents.get(currentTempoIndex);
                if (nextTempo.tick() > currentTick) break;

                long deltaTick = nextTempo.tick() - lastTick;
                double deltaMillis = (deltaTick * currentTempo) / (ppq * 1000.0);
                Thread.sleep((long) deltaMillis);
                progressBar.addCurrentMillis((long) deltaMillis);

                currentTempo = nextTempo.tempo();
                lastTick = nextTempo.tick();
                currentTempoIndex++;
            }

            long deltaTick = currentTick - lastTick;
            double deltaMillis = (deltaTick * currentTempo) / (ppq * 1000.0);
            Thread.sleep((long) deltaMillis);
            progressBar.addCurrentMillis((long) deltaMillis);
            lastTick = currentTick;

            List<MidiPlayMessage> messages = midiPlayMessages.getValues(currentTick);
            for (MidiPlayMessage msg : messages) {
                keyBoard.sendKeyboardMessage(msg.keyCode(), msg.keyType());
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

    public static int findMidiNumber(int originalKey) {
        int naturalNote = removeSharpFlat(originalKey);

        int octaveAdjusted = smartOctaveShift(naturalNote);

        return findNearestAvailableNote(octaveAdjusted);
    }

    private static int removeSharpFlat(int midi) {
        // C C→C | D D→D | E | F F→F | G G→G | A A→A | B
        int[] naturalMap = {0, 0, 2, 2, 4, 5, 5, 7, 7, 9, 9, 11};
        return (midi / 12) * 12 + naturalMap[midi % 12];
    }

    private static int smartOctaveShift(int midi) {
        int octave = midi / 12;
        int note = midi % 12;

        if (octave < 3) {
            return note + (3 * 12);
        } else if (octave > 5) {
            return note + (5 * 12);
        }
        return midi;
    }

    private static int findNearestAvailableNote(int target) {
        int closest = AVAILABLE_NOTES[0];
        int minDiff = Math.abs(target - closest);

        for (int note : AVAILABLE_NOTES) {
            int diff = Math.abs(target - note);
            if (diff < minDiff) {
                minDiff = diff;
                closest = note;
            }
        }
        return closest;
    }
}
