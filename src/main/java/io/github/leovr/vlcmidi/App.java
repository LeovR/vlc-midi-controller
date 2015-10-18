package io.github.leovr.vlcmidi;

import io.github.leovr.vlcmidi.midi.MidiNote;
import lombok.extern.slf4j.Slf4j;
import uk.co.caprica.vlcj.discovery.NativeDiscovery;
import uk.co.caprica.vlcj.filter.VideoFileFilter;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
public class App extends JFrame {

    private JComboBox<MidiDevice.Info> midiPortComboBox;
    private JPanel midiPortPanel;
    private JPanel videoFilesPanel;
    private JPanel bottomPanel;
    private static final MidiNote[] AVAILABLE_MIDI_NOTES = buildAvailableMidiNotes();
    private DefaultTableModel tableModel;
    private VlcMidiPreferences preferences;

    private static MidiNote[] buildAvailableMidiNotes() {
        return Stream.concat(Stream.of((MidiNote) null), IntStream.range(-2, 8).boxed().flatMap(octave -> Arrays.stream(MidiNote.NOTES).map(note -> new MidiNote(note, octave, 0, true)))).toArray(size -> new MidiNote[size]);
    }

    public static void main(final String[] args) {
        new NativeDiscovery().discover();
        final App app = new App();
        SwingUtilities.invokeLater(app::start);
    }

    private void start() {
        initComponents();

        preferences = new VlcMidiPreferences();

        fillMidi(preferences.getMidiPort());

        setVisible(true);
    }

    private void fillMidi(final String lastMidiPort) {
        final MidiDevice.Info[] deviceInfos = MidiSystem.getMidiDeviceInfo();
        final List<MidiDevice.Info> deviceInfoList = Arrays.stream(deviceInfos).filter(info -> {
            try {
                return MidiSystem.getMidiDevice(info).getMaxTransmitters() != 0;
            } catch (MidiUnavailableException e) {
                log.error("Could not get MIDI device: {}", info);
            }
            return false;
        }).collect(Collectors.toList());
        deviceInfoList
                .forEach(midiPortComboBox::addItem);
        if (lastMidiPort != null) {
            deviceInfoList.stream().filter(info -> lastMidiPort.equals(info.getName())).findFirst().ifPresent(midiPortComboBox::setSelectedItem);
        }
    }

    private void initComponents() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | UnsupportedLookAndFeelException | IllegalAccessException e) {
            log.error("Could not load system look and feel", e);
            System.exit(1);
        }


        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("MIDI Controlled Video Player");

        initMidiPortPanel();

        initVideoFilesPanel();

        initBottomPanel();

        initGeneralLayout();
    }

    private void initMidiPortPanel() {
        midiPortPanel = new JPanel();
        midiPortComboBox = new JComboBox<>();

        midiPortPanel.setBorder(BorderFactory.createTitledBorder("MIDI Port"));

        midiPortComboBox.setRenderer((list, value, index, isSelected, cellHasFocus) -> new JLabel(value.getName()));

        final GroupLayout midiPortPanelLayout = new GroupLayout(midiPortPanel);
        midiPortPanel.setLayout(midiPortPanelLayout);
        midiPortPanelLayout.setHorizontalGroup(
                midiPortPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(midiPortPanelLayout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(midiPortComboBox, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addContainerGap())
        );
        midiPortPanelLayout.setVerticalGroup(
                midiPortPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(midiPortComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
        );
    }

    private void initVideoFilesPanel() {
        videoFilesPanel = new JPanel();
        final JScrollPane videoFilesScrollPanel = new JScrollPane();
        final JTable videoFilesTable = new JTable();
        final JButton addVideosButton = new JButton();
        videoFilesPanel.setBorder(BorderFactory.createTitledBorder("Video Dateien"));

        tableModel = new DefaultTableModel(
                new Object[][]{
                },
                new String[]{
                        "Video Datei", "MIDI Note"
                }
        ) {
            Class[] types = new Class[]{
                    File.class, MidiNote.class
            };
            boolean[] canEdit = new boolean[]{
                    false, true
            };

            public Class getColumnClass(final int columnIndex) {
                return types[columnIndex];
            }

            public boolean isCellEditable(final int rowIndex, final int columnIndex) {
                return canEdit[columnIndex];
            }
        };
        videoFilesTable.setModel(tableModel);
        videoFilesTable.setDefaultRenderer(MidiNote.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
                if (value == null) {
                    setText("Nicht zugewiesen");
                } else {
                    final MidiNote midiNote = (MidiNote) value;
                    setText(midiNote.getNote() + " " + midiNote.getOctave() + " Ch. " + midiNote.getChannel());
                }
                return this;
            }
        });
        videoFilesTable.setDefaultRenderer(File.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
                final File file = (File) value;
                setText(file.getName());
                return this;
            }
        });
        final JComboBox<MidiNote> midiNoteComboBox = new JComboBox<>(AVAILABLE_MIDI_NOTES);
        midiNoteComboBox.setRenderer((list, value, index, isSelected, cellHasFocus) -> value == null ? new JLabel("Nicht zugewiesen") : new JLabel(value.getNote() + " " + value.getOctave() + " Ch. " + value.getChannel()));
        videoFilesTable.setDefaultEditor(MidiNote.class, new DefaultCellEditor(midiNoteComboBox));
        videoFilesTable.getTableHeader().setReorderingAllowed(false);
        videoFilesScrollPanel.setViewportView(videoFilesTable);
        if (videoFilesTable.getColumnModel().getColumnCount() > 0) {
            videoFilesTable.getColumnModel().getColumn(0).setResizable(false);
            videoFilesTable.getColumnModel().getColumn(1).setResizable(false);
        }

        addVideosButton.setText("Video hinzufügen");
        addVideosButton.addActionListener(e -> {
            final JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new FileNameExtensionFilter("Video Dateien", new VideoFileFilter().getExtensions()));
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setMultiSelectionEnabled(true);
            fileChooser.setCurrentDirectory(preferences.getCurrentDirectory());
            final int returnValue = fileChooser.showDialog(this, "Hinzufügen");
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                preferences.setCurrentDirectory(fileChooser.getCurrentDirectory());
                addFiles(fileChooser.getSelectedFiles());
            }
        });

        final GroupLayout videoFilesPanelLayout = new GroupLayout(videoFilesPanel);
        videoFilesPanel.setLayout(videoFilesPanelLayout);
        videoFilesPanelLayout.setHorizontalGroup(
                videoFilesPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(videoFilesPanelLayout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(videoFilesPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addComponent(videoFilesScrollPanel, GroupLayout.DEFAULT_SIZE, 549, Short.MAX_VALUE)
                                        .addGroup(videoFilesPanelLayout.createSequentialGroup()
                                                .addComponent(addVideosButton)
                                                .addGap(0, 0, Short.MAX_VALUE))))
        );
        videoFilesPanelLayout.setVerticalGroup(
                videoFilesPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(videoFilesPanelLayout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(videoFilesScrollPanel, GroupLayout.PREFERRED_SIZE, 404, GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(addVideosButton))
        );
    }

    private void addFiles(final File[] selectedFiles) {
        Arrays.stream(selectedFiles).forEach(file -> tableModel.addRow(new Object[]{file, null}));
    }

    private void initGeneralLayout() {
        final GroupLayout layout = new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(midiPortPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(videoFilesPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(bottomPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addContainerGap())
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(midiPortPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(videoFilesPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(bottomPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addContainerGap())
        );

        pack();
    }

    @SuppressWarnings("unchecked")
    private void initBottomPanel() {
        bottomPanel = new JPanel();
        final JButton startButton = new JButton();
        startButton.setText("Start");
        startButton.addActionListener(e -> {
            if (tableModel.getRowCount() <= 0) {
                return;
            }
            final List<VideoMidiNoteMapping> mappings = new ArrayList<>();
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                MidiNote midiNote = (MidiNote) tableModel.getValueAt(i, 1);
                if (midiNote == null) {
                    continue;
                }
                mappings.add(new VideoMidiNoteMapping((File) tableModel.getValueAt(i, 0), midiNote));
            }
            SwingUtilities.invokeLater(() -> {
                startVideo(mappings, (MidiDevice.Info) midiPortComboBox.getSelectedItem());
            });
        });

        final GroupLayout bottomPanelLayout = new GroupLayout(bottomPanel);
        bottomPanel.setLayout(bottomPanelLayout);
        bottomPanelLayout.setHorizontalGroup(
                bottomPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(bottomPanelLayout.createSequentialGroup()
                                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(startButton)
                                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        bottomPanelLayout.setVerticalGroup(
                bottomPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(bottomPanelLayout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(startButton)
                                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }

    private void startVideo(final List<VideoMidiNoteMapping> mappings, final MidiDevice.Info deviceInfo) {
        preferences.setMidiPort(deviceInfo.getName());
        final VideoPlayer videoPlayer = new VideoPlayer();
        videoPlayer.start(deviceInfo, mappings);
    }


}
