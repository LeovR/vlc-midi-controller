package io.github.leovr.vlcmidi;

import io.github.leovr.vlcmidi.midi.MidiNote;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoMidiNoteMapping {

    private File file;
    private MidiNote midiNote;
}
