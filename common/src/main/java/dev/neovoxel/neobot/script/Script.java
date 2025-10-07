package dev.neovoxel.neobot.script;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class Script {
    private final int schemaVersion;
    private final String name;
    private final String author;
    private final String version;
    private final String entrypoint;
    private String description;

    public String toExpression() {
        return author + ":" + name + ":" + version;
    }

    public void load() {

    }
}
