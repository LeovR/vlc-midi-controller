package io.github.leovr.vlcmidi;

import com.beust.jcommander.Parameter;
import lombok.Getter;

@Getter
public class Options {

    @Parameter(names = {"--sound","-s"}, description = "Sound")
    private boolean sound = false;

    @Parameter(names = {"-c", "--caching"}, description = "Filecaching milliseconds")
    private Integer cachingMilliseconds;

    @Parameter(names={"-b","--bonjour"},description = "Enable bonjour")
    private boolean bonjour = false;

}
