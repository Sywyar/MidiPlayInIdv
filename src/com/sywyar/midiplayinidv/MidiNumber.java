package com.sywyar.midiplayinidv;

public enum MidiNumber {
    q(81),
    w(83),
    e(84),
    r(86),
    t(88),
    y(89),
    u(91),
    a(69),
    s(71),
    d(72),
    f(74),
    g(76),
    h(77),
    j(79),
    z(57),
    x(59),
    c(60),
    v(62),
    b(64),
    n(65),
    m(67);


    private final int midiNumber;

    MidiNumber(int midiNumber) {
        this.midiNumber = midiNumber;
    }

    public int getMidiNumber() {
        return midiNumber;
    }
}
