package io.github.leovr.vlcmidi.midi;

public interface MidiNoteListener {

    void onMidiNoteStart(final MidiNote midiNote);

    void onMidiNoteEnd(final MidiNote midiNote);

    void onMidiNote(final MidiNote midiNote);

}
