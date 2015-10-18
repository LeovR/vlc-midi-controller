package io.github.leovr.vlcmidi.midi;

import lombok.extern.slf4j.Slf4j;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class MidiNoteReceiver implements Receiver {

    private final List<MidiNoteListener> listeners = new ArrayList<>();

    @Override
    public void send(final MidiMessage message, final long timeStamp) {
        if (!(message instanceof ShortMessage)) {
            return;
        }
        final ShortMessage shortMessage = (ShortMessage) message;
        final int octave = (shortMessage.getData1() / 12) - 2;
        final int noteIndex = (shortMessage.getData1() % 12);
        final String noteName = MidiNote.NOTES[noteIndex];
        final boolean start = shortMessage.getCommand() == ShortMessage.NOTE_ON;
        final MidiNote note = new MidiNote(noteName, octave, shortMessage.getChannel(), start);
        log.debug("Received note: {}", note);
        listeners.forEach(midiNoteListener -> midiNoteListener.onMidiNote(note));
        if (start) {
            listeners.forEach(midiNoteListener -> midiNoteListener.onMidiNoteStart(note));
        } else {
            listeners.forEach(midiNoteListener -> midiNoteListener.onMidiNoteEnd(note));
        }
    }

    public void registerMidiNoteListener(final MidiNoteListener listener) {
        listeners.add(listener);
    }

    public boolean unregisterMidiNoteListener(final MidiNoteListener listener) {
        return listeners.remove(listener);
    }

    @Override
    public void close() {
    }
}
