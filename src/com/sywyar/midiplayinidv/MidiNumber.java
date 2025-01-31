package com.sywyar.midiplayinidv;

public enum MidiNumber {
    q(72),  // C5
    w(74),  // D5
    e(76),  // E5
    r(77),  // F5
    t(79),  // G5
    y(81),  // A5
    u(83),  // B5
    a(60),  // C4
    s(62),  // D4
    d(64),  // E4
    f(65),  // F4
    g(67),  // G4
    h(69),  // A4
    j(71),  // B4
    z(48),  // C3
    x(50),  // D3
    c(52),  // E3
    v(53),  // F3
    b(55),  // G3
    n(57),  // A3
    m(59);  // B3


    private final int midiNumber;

    MidiNumber(int midiNumber) {
        this.midiNumber = midiNumber;
    }

    public int getMidiNumber() {
        return midiNumber;
    }
}
