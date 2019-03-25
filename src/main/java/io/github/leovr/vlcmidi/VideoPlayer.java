package io.github.leovr.vlcmidi;

import io.github.leovr.rtipmidi.AppleMidiServer;
import io.github.leovr.rtipmidi.MidiReceiverAppleMidiSession;
import io.github.leovr.vlcmidi.midi.MidiControlChangeListenerAdapter;
import io.github.leovr.vlcmidi.midi.MidiNote;
import io.github.leovr.vlcmidi.midi.MidiNoteListenerAdapter;
import io.github.leovr.vlcmidi.midi.MidiNoteReceiver;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.medialist.MediaApi;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaListPlayerComponent;
import uk.co.caprica.vlcj.player.component.MediaPlayerSpecs;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.player.embedded.fullscreen.adaptive.AdaptiveFullScreenStrategy;
import uk.co.caprica.vlcj.player.list.MediaListPlayer;
import uk.co.caprica.vlcj.player.list.PlaybackMode;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class VideoPlayer {


    private final Options options;

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
        this.options = options;
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

        final String[] args;
        if (options.getCachingMilliseconds() == null) {
            args = new String[]{"--video-title=vlcj video output", "--no-snapshot-preview", "--quiet", "--intf=dummy",
                    "--file-caching=0", "--disc-caching=0"};
        } else {
            args = new String[]{"--video-title=vlcj video output", "--no-snapshot-preview", "--quiet", "--intf=dummy",
                    "--file-caching=" + options.getCachingMilliseconds(),
                    "--disc-caching=" + options.getCachingMilliseconds()};
        }

        mediaPlayerComponent = new EmbeddedMediaListPlayerComponent(
                MediaPlayerSpecs.embeddedMediaPlayerSpec().withFactory(new MediaPlayerFactory(args))) {
            @Override
            public void keyPressed(final KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ESCAPE:
                        mediaPlayer.fullScreen().set(false);
                        normalCursor();
                        break;
                    case KeyEvent.VK_ENTER:
                        mediaPlayer.fullScreen().toggle();
                        break;
                    case KeyEvent.VK_SPACE:
                        stop();
                        break;
                }
            }

            @Override
            public void mousePressed(final MouseEvent e) {
                if (e.getClickCount() == 2) {
                    mediaPlayer.fullScreen().toggle();
                    if (mediaPlayer.fullScreen().isFullScreen()) {
                        transparentCursor();
                    } else {
                        normalCursor();
                    }
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
        mediaPlayer = mediaPlayerComponent.mediaPlayer();
        frame.setVisible(true);
        mediaListPlayer = mediaPlayerComponent.mediaListPlayer();
        mediaPlayer.fullScreen().strategy(new AdaptiveFullScreenStrategy(frame));

        if (!options.isSound()) {
            mediaPlayer.audio().mute();
        }

        mediaPlayer.controls().setRepeat(false);
        mediaListPlayer.controls().setMode(PlaybackMode.DEFAULT);

    }

    private void normalCursor() {
        frame.setCursor(Cursor.getDefaultCursor());
    }

    private void blackScreen() {
        cardLayout.show(frame.getContentPane(), BLACK_PANEL);
    }


    public void start(final MidiDevice.Info deviceInfo, final List<VideoMidiNoteMapping> mappings) {
        initMediaList(mappings);

        initMidi(deviceInfo);

        start();
    }

    public void startRtpMidi(final List<VideoMidiNoteMapping> mappings) {
        initMediaList(mappings);

        initRtpMidi();

        start();
    }

    private void start() {
        mediaPlayer.fullScreen().set(true);
        transparentCursor();
    }

    private void transparentCursor() {
        frame.setCursor(frame.getToolkit()
                .createCustomCursor(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(), null));
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
        mediaListPlayer.controls().stop();
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
                    mediaPlayer.media().prepare(mediaListPlayer.list().media().mrl(index));
                });
            }

            @Override
            public void onMidiNoteEnd(final MidiNote midiNote) {
                final Integer index = midiNoteMapping.get(new MidiNoteKey(midiNote));
                if (index == null) {
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    log.info("Starting video {}", index);
                    mediaListPlayer.controls().play(index);
                    showVideoPlayer();
                    if (!options.isSound()) {
                        mediaPlayer.audio().mute();
                    }
                });
            }
        });
    }

    private void showVideoPlayer() {
        cardLayout.show(frame.getContentPane(), VIDEO_PLAYER);
    }

    private void initMediaList(final List<VideoMidiNoteMapping> mappings) {
        final MediaApi mediaApi = mediaListPlayer.list().media();

        midiNoteMapping.clear();
        for (int i = 0; i < mappings.size(); i++) {
            final VideoMidiNoteMapping mapping = mappings.get(i);
            mediaApi.add(mapping.getFile().getAbsolutePath());
            midiNoteMapping.put(new MidiNoteKey(mapping.getMidiNote()), i);
        }
    }
}
