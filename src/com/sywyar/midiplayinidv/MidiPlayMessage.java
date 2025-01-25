package com.sywyar.midiplayinidv;

import com.sywyar.keyboard.keyboardenum.KeyCodeEnum;
import com.sywyar.keyboard.keyboardenum.KeyTypeEnum;

public record MidiPlayMessage(KeyCodeEnum keyCode, KeyTypeEnum keyType) {
}
