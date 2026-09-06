package autismclient.util;

import java.util.List;

public record AutismPacketFieldValue(AutismPacketSchemaRegistry.FieldSchema schema,
                                     Object rawValue,
                                     List<String> formattedLines,
                                     boolean readable,
                                     boolean editableCandidate) {
    public AutismPacketFieldValue {
        formattedLines = formattedLines == null ? List.of() : List.copyOf(formattedLines);
    }

    public String name() {
        return schema == null ? "field" : schema.name();
    }

    public String javaType() {
        return schema == null ? "Object" : schema.javaType();
    }

    public String valueKind() {
        return schema == null ? "object" : schema.valueKind();
    }

    public String summary() {
        return formattedLines.isEmpty() ? "unavailable" : formattedLines.getFirst();
    }

    public List<String> details() {
        return formattedLines.size() <= 1 ? List.of() : formattedLines.subList(1, formattedLines.size());
    }
}
