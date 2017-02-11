package io.github.leovr.vlcmidi;

import com.beust.jcommander.JCommander;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leovr.vlcmidi.midi.MidiNote;
import lombok.extern.slf4j.Slf4j;
import uk.co.caprica.vlcj.discovery.NativeDiscovery;
import uk.co.caprica.vlcj.filter.VideoFileFilter;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.LayoutStyle;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Options options;
    private JmDNS jmdns;

    public App(final Options options) {
        this.options = options;
    }

    private static MidiNote[] buildAvailableMidiNotes() {
        return Stream.concat(Stream.of((MidiNote) null), IntStream.range(-2, 8).boxed()
                .flatMap(octave -> Arrays.stream(MidiNote.NOTES).map(note -> new MidiNote(note, octave, 0, true))))
                .toArray(MidiNote[]::new);
    }

    public static void main(final String[] args) {
        new NativeDiscovery().discover();
        final Options options = new Options();
        new JCommander(options, args);
        final App app = new App(options);
        SwingUtilities.invokeLater(app::start);
    }

    private void start() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                if (options.isBonjour() && jmdns != null) {
                    jmdns.unregisterAllServices();
                }
            }
        });

        initComponents();

        preferences = new VlcMidiPreferences();

        fillMidi(preferences.getMidiPort());

        startBonjour();

        setVisible(true);

    }


    private void startBonjour() {
        if (!options.isBonjour()) {
            return;
        }
        try {
            final String interfaceName = Optional.ofNullable(options.getNetworkInterfaceName()).orElse("");
            final InetAddress jmDnsInetAddress = Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                    .filter(networkInterface -> networkInterface.getName().equals(interfaceName)).findFirst().flatMap(
                            networkInterface -> Collections.list(networkInterface.getInetAddresses()).stream()
                                    .filter(inetAddress -> inetAddress instanceof Inet4Address).findFirst())
                    .orElseGet(() -> {
                        try {
                            return InetAddress.getLocalHost();
                        } catch (final UnknownHostException e) {
                            throw new RuntimeException(e);
                        }
                    });

            jmdns = JmDNS.create(jmDnsInetAddress);
            final ServiceInfo serviceInfo =
                    ServiceInfo.create("_apple-midi._udp.local.", "VLC MIDI Player", 50004, "apple-midi");
            jmdns.registerService(serviceInfo);
        } catch (final IOException e) {
            log.error("IOException creating JmDNS", e);
        }

    }

    private void fillMidi(final String lastMidiPort) {
        if (options.isBonjour()) {
            return;
        }
        final MidiDevice.Info[] deviceInfos = MidiSystem.getMidiDeviceInfo();
        final List<MidiDevice.Info> deviceInfoList = Arrays.stream(deviceInfos).filter(info -> {
            try {
                return MidiSystem.getMidiDevice(info).getMaxTransmitters() != 0;
            } catch (MidiUnavailableException e) {
                log.error("Could not get MIDI device: {}", info);
            }
            return false;
        }).collect(Collectors.toList());
        deviceInfoList.forEach(midiPortComboBox::addItem);
        if (lastMidiPort != null) {
            deviceInfoList.stream().filter(info -> lastMidiPort.equals(info.getName())).findFirst()
                    .ifPresent(midiPortComboBox::setSelectedItem);
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

        initMenu();

        initMidiPortPanel();

        initVideoFilesPanel();

        initBottomPanel();

        initGeneralLayout();
    }

    private void initMenu() {
        final JMenuBar menuBar = new JMenuBar();

        final JMenu menu = new JMenu("Datei");
        menu.setMnemonic(KeyEvent.VK_D);

        final JMenuItem menuItemLoad = new JMenuItem("Videoliste laden", KeyEvent.VK_L);
        menuItemLoad.addActionListener(e -> loadVideoList());
        menu.add(menuItemLoad);
        final JMenuItem menuItemSave = new JMenuItem("Videoliste speichern", KeyEvent.VK_S);
        menuItemSave.addActionListener(e -> saveVideoList());
        menu.add(menuItemSave);

        menuBar.add(menu);

        setJMenuBar(menuBar);
    }

    private void saveVideoList() {
        final JFileChooser fileChooser = new JFileChooser(preferences.getConfigCurrentDirectory());
        fileChooser.setFileFilter(new FileNameExtensionFilter("JSON Datei", "json"));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);
        final int returnValue = fileChooser.showDialog(this, "Speichern");
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            preferences.setConfigCurrentDirectory(fileChooser.getCurrentDirectory());
            final File saveDestination;
            if (!fileChooser.getSelectedFile().getAbsolutePath().endsWith(".json")) {
                saveDestination = new File(fileChooser.getSelectedFile() + ".json");
            } else {
                saveDestination = fileChooser.getSelectedFile();
            }
            saveVideoList(saveDestination);
        }
    }

    private void loadVideoList() {
        final JFileChooser fileChooser = new JFileChooser(preferences.getConfigCurrentDirectory());
        fileChooser.setFileFilter(new FileNameExtensionFilter("JSON Datei", "json"));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);
        final int returnValue = fileChooser.showDialog(this, "Laden");
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            preferences.setConfigCurrentDirectory(fileChooser.getCurrentDirectory());
            loadVideoList(fileChooser.getSelectedFile());
        }
    }

    private void loadVideoList(final File loadDestination) {
        if (!loadDestination.exists()) {
            return;
        }

        try {
            final VlcMidiSaveFile saveFile = objectMapper.readValue(loadDestination, VlcMidiSaveFile.class);
            fillTableModel(saveFile.getMappings());
        } catch (final IOException e) {
            log.error("Could not read save file: {}", loadDestination, e);
        }
    }

    private void fillTableModel(final List<VideoMidiNoteMapping> mappings) {
        tableModel.getDataVector().removeAllElements();
        mappings.forEach(mapping -> tableModel.addRow(new Object[]{mapping.getFile(), mapping.getMidiNote()}));
    }

    private void saveVideoList(final File saveDestination) {
        if (saveDestination.exists() && !saveDestination.canWrite()) {
            return;
        }

        final VlcMidiSaveFile saveFile = new VlcMidiSaveFile(getVideoMidiNoteMappings());
        try {
            objectMapper.writer().writeValue(saveDestination, saveFile);
        } catch (final IOException e) {
            log.error("Could not write save file: {} to {}", saveFile, saveDestination, e);
        }
    }

    private void initMidiPortPanel() {
        midiPortPanel = new JPanel();
        midiPortComboBox = new JComboBox<>();
        if (options.isBonjour()) {
            return;
        }

        midiPortPanel.setBorder(BorderFactory.createTitledBorder("MIDI Port"));

        midiPortComboBox.setRenderer((list, value, index, isSelected, cellHasFocus) -> new JLabel(value.getName()));

        final GroupLayout midiPortPanelLayout = new GroupLayout(midiPortPanel);
        midiPortPanel.setLayout(midiPortPanelLayout);
        midiPortPanelLayout.setHorizontalGroup(midiPortPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(midiPortPanelLayout.createSequentialGroup().addContainerGap()
                        .addComponent(midiPortComboBox, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap()));
        midiPortPanelLayout.setVerticalGroup(midiPortPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(midiPortComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                        GroupLayout.PREFERRED_SIZE));
    }

    private void initVideoFilesPanel() {
        videoFilesPanel = new JPanel();
        final JScrollPane videoFilesScrollPanel = new JScrollPane();
        final JTable videoFilesTable = new JTable();
        final JButton addVideosButton = new JButton();
        videoFilesPanel.setBorder(BorderFactory.createTitledBorder("Video Dateien"));

        tableModel = new DefaultTableModel(new Object[][]{}, new String[]{"Video Datei", "MIDI Note"}) {
            Class[] types = new Class[]{File.class, MidiNote.class};
            boolean[] canEdit = new boolean[]{false, true};

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
            public Component getTableCellRendererComponent(final JTable table, final Object value,
                                                           final boolean isSelected, final boolean hasFocus,
                                                           final int row, final int column) {
                if (value == null) {
                    setText("Nicht zugewiesen");
                } else {
                    final MidiNote midiNote = (MidiNote) value;
                    setText(midiNote.getNote() + " " + midiNote.getOctave() + " Ch. " + (midiNote.getChannel() + 1));
                }
                return this;
            }
        });
        videoFilesTable.setDefaultRenderer(File.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(final JTable table, final Object value,
                                                           final boolean isSelected, final boolean hasFocus,
                                                           final int row, final int column) {
                final File file = (File) value;
                setText(file.getName());
                setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                return this;
            }
        });
        final JComboBox<MidiNote> midiNoteComboBox = new JComboBox<>(AVAILABLE_MIDI_NOTES);
        midiNoteComboBox.setRenderer(
                (list, value, index, isSelected, cellHasFocus) -> value == null ? new JLabel("Nicht zugewiesen") :
                        new JLabel(value.getNote() + " " + value.getOctave() + " Ch. " + (value.getChannel()) + 1));
        videoFilesTable.setDefaultEditor(MidiNote.class, new DefaultCellEditor(midiNoteComboBox));
        videoFilesTable.getTableHeader().setReorderingAllowed(false);
        videoFilesTable.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteRow");
        videoFilesTable.getActionMap().put("deleteRow", new AbstractAction() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                Arrays.stream(videoFilesTable.getSelectedRows()).forEach(row -> tableModel.removeRow(row));
            }
        });
        videoFilesScrollPanel.setViewportView(videoFilesTable);
        if (videoFilesTable.getColumnModel().getColumnCount() > 0) {
            videoFilesTable.getColumnModel().getColumn(0).setResizable(false);
            videoFilesTable.getColumnModel().getColumn(1).setResizable(false);
        }

        addVideosButton.setText("Video hinzufügen");
        addVideosButton.addActionListener(e -> {
            final JFileChooser fileChooser = new JFileChooser(preferences.getCurrentDirectory());
            fileChooser
                    .setFileFilter(new FileNameExtensionFilter("Video Dateien", new VideoFileFilter().getExtensions()));
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setMultiSelectionEnabled(true);
            final int returnValue = fileChooser.showDialog(this, "Hinzufügen");
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                preferences.setCurrentDirectory(fileChooser.getCurrentDirectory());
                addFiles(fileChooser.getSelectedFiles());
            }
        });

        final GroupLayout videoFilesPanelLayout = new GroupLayout(videoFilesPanel);
        videoFilesPanel.setLayout(videoFilesPanelLayout);
        videoFilesPanelLayout.setHorizontalGroup(
                videoFilesPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(
                        videoFilesPanelLayout.createSequentialGroup().addContainerGap().addGroup(
                                videoFilesPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addComponent(videoFilesScrollPanel, GroupLayout.DEFAULT_SIZE, 549,
                                                Short.MAX_VALUE).addGroup(
                                        videoFilesPanelLayout.createSequentialGroup().addComponent(addVideosButton)
                                                .addGap(0, 0, Short.MAX_VALUE)))));
        videoFilesPanelLayout.setVerticalGroup(videoFilesPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(videoFilesPanelLayout.createSequentialGroup().addContainerGap()
                        .addComponent(videoFilesScrollPanel, GroupLayout.PREFERRED_SIZE, 404,
                                GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, GroupLayout.DEFAULT_SIZE,
                                Short.MAX_VALUE).addComponent(addVideosButton)));
    }

    private void addFiles(final File[] selectedFiles) {
        Arrays.stream(selectedFiles).forEach(file -> tableModel.addRow(new Object[]{file, null}));
    }

    private void initGeneralLayout() {
        final GroupLayout layout = new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(midiPortPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(videoFilesPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createSequentialGroup().addContainerGap()
                        .addComponent(bottomPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap()));
        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(
                layout.createSequentialGroup()
                        .addComponent(midiPortPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                                GroupLayout.PREFERRED_SIZE).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(videoFilesPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE,
                                Short.MAX_VALUE).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bottomPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE,
                                GroupLayout.PREFERRED_SIZE).addContainerGap()));

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
            final List<VideoMidiNoteMapping> mappings = getVideoMidiNoteMappings().stream()
                    .filter(videoMidiNoteMapping -> videoMidiNoteMapping.getMidiNote() != null)
                    .collect(Collectors.toList());
            SwingUtilities
                    .invokeLater(() -> startVideo(mappings, (MidiDevice.Info) midiPortComboBox.getSelectedItem()));
        });

        final GroupLayout bottomPanelLayout = new GroupLayout(bottomPanel);
        bottomPanel.setLayout(bottomPanelLayout);
        bottomPanelLayout.setHorizontalGroup(bottomPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(bottomPanelLayout.createSequentialGroup()
                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE).addComponent(startButton)
                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
        bottomPanelLayout.setVerticalGroup(bottomPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(bottomPanelLayout.createSequentialGroup().addContainerGap().addComponent(startButton)
                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
    }

    private List<VideoMidiNoteMapping> getVideoMidiNoteMappings() {
        final List<VideoMidiNoteMapping> mappings = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            final MidiNote midiNote = (MidiNote) tableModel.getValueAt(i, 1);
            mappings.add(new VideoMidiNoteMapping((File) tableModel.getValueAt(i, 0), midiNote));
        }
        return mappings;
    }

    private void startVideo(final List<VideoMidiNoteMapping> mappings, final MidiDevice.Info deviceInfo) {
        final VideoPlayer videoPlayer = new VideoPlayer(options);
        if (options.isBonjour()) {
            videoPlayer.startRtpMidi(mappings);
        } else {
            preferences.setMidiPort(deviceInfo.getName());
            videoPlayer.start(deviceInfo, mappings);
        }
    }

}
