package io.github.leovr.vlcmidi;

import io.github.leovr.vlcmidi.midi.MidiControlChangeListenerAdapter;
import io.github.leovr.vlcmidi.midi.MidiNote;
import io.github.leovr.vlcmidi.midi.MidiNoteListenerAdapter;
import io.github.leovr.vlcmidi.midi.MidiNoteReceiver;
import uk.co.caprica.vlcj.component.EmbeddedMediaListPlayerComponent;
import uk.co.caprica.vlcj.medialist.MediaList;
import uk.co.caprica.vlcj.player.MediaPlayer;
import uk.co.caprica.vlcj.player.embedded.DefaultAdaptiveRuntimeFullScreenStrategy;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.player.list.MediaListPlayer;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoPlayer {

    public static final String BLACK_PANEL = "blackPanel";
    public static final String VIDEO_PLAYER = "videoPlayer";
    private final JFrame frame;

    private final EmbeddedMediaListPlayerComponent mediaPlayerComponent;
    private final Map<MidiNote, Integer> midiNoteMapping = new HashMap<>();
    private final CardLayout cardLayout;
    private MidiDevice midiDevice;
    private final MediaListPlayer mediaListPlayer;
    private final EmbeddedMediaPlayer mediaPlayer;

    public VideoPlayer() {
        frame = new JFrame("Video Player");
        frame.setBounds(100, 100, 600, 400);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                mediaPlayerComponent.release();
                midiDevice.close();
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
                return new String[]{"--file-caching=0", "--disc-caching=0"};
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

        mediaPlayer.mute();

    }

    private void blackScreen() {
        cardLayout.show(frame.getContentPane(), BLACK_PANEL);
    }


    public void start(final MidiDevice.Info deviceInfo, final List<VideoMidiNoteMapping> mappings) {
        initMediaList(mappings);

        initMidi(deviceInfo);

        mediaPlayer.setFullScreen(true);
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
                final Integer index = midiNoteMapping.get(midiNote);
                if (index == null) {
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    showVideoPlayer();
                    mediaListPlayer.playItem(index);
                });
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
            midiNoteMapping.put(mapping.getMidiNote(), i);
        }
    }
}
