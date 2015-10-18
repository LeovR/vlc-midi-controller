package io.github.leovr.vlcmidi;

import java.io.File;
import java.util.prefs.Preferences;

public class VlcMidiPreferences {
    private static final String MIDI_PORT = "midiPort";
    private static final String CURRENT_DIRECTORY = "currentDirectory";
    private final Preferences preferences;

    public VlcMidiPreferences() {
        this.preferences = Preferences.userNodeForPackage(VlcMidiPreferences.class);
    }

    public String getMidiPort() {
        return preferences.get(MIDI_PORT, null);
    }

    public void setMidiPort(final String midiPort) {
        preferences.put(MIDI_PORT, midiPort);
    }

    public File getCurrentDirectory() {
        final String currentDirectory = preferences.get(CURRENT_DIRECTORY, null);
        if (currentDirectory == null) {
            return null;
        }
        return new File(currentDirectory);
    }

    public void setCurrentDirectory(final File currentDirectory) {
        if (currentDirectory == null) {
            return;
        }
        preferences.put(CURRENT_DIRECTORY, currentDirectory.getAbsolutePath());
    }
}
