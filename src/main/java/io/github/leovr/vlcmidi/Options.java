package io.github.leovr.vlcmidi;

import com.beust.jcommander.Parameter;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class Options {

    @Parameter
    private List<String> videoMappings = new ArrayList<>();

    @Parameter(names = {"--sound","-s"}, description = "Sound")
    private boolean sound = false;

    @Parameter(names = {"-c", "--caching"}, description = "Filecaching milliseconds")
    private Integer cachingMilliseconds;

    @Parameter(names={"-b","--bonjour"},description = "Enable bonjour")
    private boolean bonjour = false;

    @Parameter(names = {"-i", "--interface"}, description = "Network interface to bind JmDNS to")
    private String networkInterfaceName;

}
