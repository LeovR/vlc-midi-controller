package io.github.leovr.vlcmidi.midi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MidiNote {

    public static final String[] NOTES = new String[]{"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    private String note;
    private int octave;
    private int channel;
    private boolean start;
}
