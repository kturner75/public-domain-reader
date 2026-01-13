package org.example.reader.model;

public record IllustrationSettings(
    String style,
    String promptPrefix,
    String reasoning
) {
    public static IllustrationSettings defaults() {
        return new IllustrationSettings(
            "vintage book illustration",
            "vintage book illustration style, detailed pen and ink with subtle watercolor tints,",
            "Default classic illustration style"
        );
    }
}
