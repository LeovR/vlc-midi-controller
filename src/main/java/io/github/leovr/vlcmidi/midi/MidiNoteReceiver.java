package io.github.leovr.vlcmidi.midi;

import lombok.extern.slf4j.Slf4j;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class MidiNoteReceiver implements Receiver {

    private final List<MidiNoteListener> midiNoteListeners = new ArrayList<>();
    private final List<MidiControlChangeListener> midiControlChangeListeners = new ArrayList<>();

    @Override
    public void send(final MidiMessage message, final long timeStamp) {
        if (!(message instanceof ShortMessage)) {
            return;
        }
        final ShortMessage shortMessage = (ShortMessage) message;
        switch (shortMessage.getCommand()) {
            case ShortMessage.NOTE_ON:
            case ShortMessage.NOTE_OFF:
                handleNormalNote(shortMessage);
                break;
            case ShortMessage.CONTROL_CHANGE:
                handleControlChange(shortMessage);
                break;
        }
    }

    private void handleControlChange(final ShortMessage message) {
        log.debug("Received control change message: {} {}", message.getData1(), message.getData2());
        if (message.getData1() == 123 && message.getData2() == 0) {
            midiControlChangeListeners.forEach(MidiControlChangeListener::onAllNotesOff);
        }
    }

    private void handleNormalNote(final ShortMessage message) {
        final int octave = (message.getData1() / 12) - 2;
        final int noteIndex = (message.getData1() % 12);
        final String noteName = MidiNote.NOTES[noteIndex];
        final boolean start = message.getCommand() == ShortMessage.NOTE_ON;
        final MidiNote note = new MidiNote(noteName, octave, message.getChannel(), start);
        log.debug("Received note: {}", note);
        midiNoteListeners.forEach(midiNoteListener -> midiNoteListener.onMidiNote(note));
        if (start) {
            midiNoteListeners.forEach(midiNoteListener -> midiNoteListener.onMidiNoteStart(note));
        } else {
            midiNoteListeners.forEach(midiNoteListener -> midiNoteListener.onMidiNoteEnd(note));
        }
    }

    public void registerMidiNoteListener(final MidiNoteListener listener) {
        midiNoteListeners.add(listener);
    }

    public boolean unregisterMidiNoteListener(final MidiNoteListener listener) {
        return midiNoteListeners.remove(listener);
    }

    public void registerMidiControlChangeListener(final MidiControlChangeListener listener) {
        midiControlChangeListeners.add(listener);
    }

    public boolean unregisterMidiControlChangeListener(final MidiControlChangeListener listener) {
        return midiControlChangeListeners.remove(listener);
    }

    @Override
    public void close() {
    }
}
