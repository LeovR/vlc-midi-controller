package io.github.leovr.vlcmidi;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.leovr.vlcmidi.midi.MidiNote;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;

@Data
@NoArgsConstructor
public class VideoMidiNoteMapping {

    public VideoMidiNoteMapping(final File file, final MidiNote midiNote) {
        this.filePath = file.getAbsolutePath();
        this.midiNote = midiNote;
    }

    private String filePath;
    private MidiNote midiNote;

    @JsonIgnore
    public File getFile() {
        return new File(filePath);
    }
}
