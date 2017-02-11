package io.github.leovr.vlcmidi;

import io.github.leovr.rtipmidi.AppleMidiServer;
import io.github.leovr.rtipmidi.MidiReceiverAppleMidiSession;
import io.github.leovr.vlcmidi.midi.MidiControlChangeListenerAdapter;
import io.github.leovr.vlcmidi.midi.MidiNote;
import io.github.leovr.vlcmidi.midi.MidiNoteListenerAdapter;
import io.github.leovr.vlcmidi.midi.MidiNoteReceiver;
import lombok.EqualsAndHashCode;
import uk.co.caprica.vlcj.component.EmbeddedMediaListPlayerComponent;
import uk.co.caprica.vlcj.medialist.MediaList;
import uk.co.caprica.vlcj.player.MediaPlayer;
import uk.co.caprica.vlcj.player.embedded.DefaultAdaptiveRuntimeFullScreenStrategy;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.player.list.MediaListPlayer;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoPlayer {


    @EqualsAndHashCode
    private static class MidiNoteKey {

        private final String note;
        private final int octave;
        private final int channel;

        MidiNoteKey(final MidiNote midiNote) {
            note = midiNote.getNote();
            octave = midiNote.getOctave();
            channel = midiNote.getChannel();
        }
    }

    private static final String BLACK_PANEL = "blackPanel";
    private static final String VIDEO_PLAYER = "videoPlayer";
    private final JFrame frame;

    private final EmbeddedMediaListPlayerComponent mediaPlayerComponent;
    private final Map<MidiNoteKey, Integer> midiNoteMapping = new HashMap<>();
    private final CardLayout cardLayout;
    private MidiDevice midiDevice;
    private final MediaListPlayer mediaListPlayer;
    private final EmbeddedMediaPlayer mediaPlayer;
    private AppleMidiServer appleMidiServer;

    public VideoPlayer(final Options options) {
        frame = new JFrame("Video Player");
        frame.setBounds(100, 100, 600, 400);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                mediaPlayerComponent.release();
                if (midiDevice != null) {
                    midiDevice.close();
                }
                if (appleMidiServer != null) {
                    appleMidiServer.stop();
                }
                frame.dispose();
            }
        });

        mediaPlayerComponent = new EmbeddedMediaListPlayerComponent() {
            @Override
            public void keyPressed(final KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ESCAPE:
                        mediaPlayer.setFullScreen(false);
                        break;
                    case KeyEvent.VK_ENTER:
                        mediaPlayer.setFullScreen(!mediaPlayer.isFullScreen());
                        break;
                    case KeyEvent.VK_SPACE:
                        stop();
                        break;
                }
            }

            @Override
            protected String[] onGetMediaPlayerFactoryExtraArgs() {
                if (options.getCachingMilliseconds() == null) {
                    return new String[]{"--file-caching=0", "--disc-caching=0"};
                }
                return new String[]{"--file-caching=" + options.getCachingMilliseconds(),
                        "--disc-caching=" + options.getCachingMilliseconds()};
            }

            @Override
            public void mousePressed(final MouseEvent e) {
                if (e.getClickCount() == 2) {
                    mediaPlayer.toggleFullScreen();
                }
            }

            @Override
            public void finished(final MediaPlayer mediaPlayer) {
                blackScreen();
            }
        };

        cardLayout = new CardLayout();
        frame.getContentPane().setLayout(cardLayout);
        frame.getContentPane().add(mediaPlayerComponent, VIDEO_PLAYER);
        final JPanel blackPanel = new JPanel();
        blackPanel.setBackground(Color.BLACK);
        frame.getContentPane().add(blackPanel, BLACK_PANEL);
        mediaPlayer = mediaPlayerComponent.getMediaPlayer();
        frame.setVisible(true);
        mediaListPlayer = mediaPlayerComponent.getMediaListPlayer();
        mediaPlayer.setFullScreenStrategy(new DefaultAdaptiveRuntimeFullScreenStrategy(frame));

        if (!options.isSound()) {
            mediaPlayer.mute();
        }

        mediaPlayer.setRepeat(false);

    }

    private void blackScreen() {
        cardLayout.show(frame.getContentPane(), BLACK_PANEL);
    }


    public void start(final MidiDevice.Info deviceInfo, final List<VideoMidiNoteMapping> mappings) {
        initMediaList(mappings);

        initMidi(deviceInfo);

        mediaPlayer.setFullScreen(true);
    }

    public void startRtpMidi(final List<VideoMidiNoteMapping> mappings) {
        initMediaList(mappings);

        initRtpMidi();

        mediaPlayer.setFullScreen(true);
    }

    private void initRtpMidi() {

        appleMidiServer = new AppleMidiServer("VLC MIDI Player", 50004);

        final MidiNoteReceiver receiver = new MidiNoteReceiver();
        registerMidiNoteListener(receiver);
        registerMidiControlChangeListener(receiver);

        appleMidiServer.addAppleMidiSession(new MidiReceiverAppleMidiSession(receiver));
        appleMidiServer.start();

    }

    private void initMidi(final MidiDevice.Info deviceInfo) {
        try {
            midiDevice = MidiSystem.getMidiDevice(deviceInfo);
            if (midiDevice.isOpen()) {
                throw new RuntimeException();
            }
            midiDevice.open();

            final MidiNoteReceiver receiver = new MidiNoteReceiver();
            registerMidiNoteListener(receiver);
            registerMidiControlChangeListener(receiver);

            midiDevice.getTransmitter().setReceiver(receiver);
        } catch (final MidiUnavailableException e) {
            throw new RuntimeException(e);
        }
    }

    private void registerMidiControlChangeListener(final MidiNoteReceiver receiver) {
        receiver.registerMidiControlChangeListener(new MidiControlChangeListenerAdapter() {
            @Override
            public void onAllNotesOff() {
                stop();
            }
        });
    }

    private void stop() {
        blackScreen();
        mediaListPlayer.stop();
    }

    private void registerMidiNoteListener(final MidiNoteReceiver receiver) {
        receiver.registerMidiNoteListener(new MidiNoteListenerAdapter() {
            @Override
            public void onMidiNoteStart(final MidiNote midiNote) {
                final Integer index = midiNoteMapping.get(new MidiNoteKey(midiNote));
                if (index == null) {
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    showVideoPlayer();
                    mediaPlayer.prepareMedia(mediaListPlayer.getMediaList().items().get(index).mrl());
                });
            }

            @Override
            public void onMidiNoteEnd(final MidiNote midiNote) {
                final Integer index = midiNoteMapping.get(new MidiNoteKey(midiNote));
                if (index == null) {
                    return;
                }
                SwingUtilities.invokeLater(() -> mediaListPlayer.playItem(index));
            }
        });
    }

    private void showVideoPlayer() {
        cardLayout.show(frame.getContentPane(), VIDEO_PLAYER);
    }

    private void initMediaList(final List<VideoMidiNoteMapping> mappings) {
        final MediaList mediaList = mediaListPlayer.getMediaList();

        midiNoteMapping.clear();
        for (int i = 0; i < mappings.size(); i++) {
            final VideoMidiNoteMapping mapping = mappings.get(i);
            mediaList.addMedia(mapping.getFile().getAbsolutePath());
            midiNoteMapping.put(new MidiNoteKey(mapping.getMidiNote()), i);
        }
    }
}
