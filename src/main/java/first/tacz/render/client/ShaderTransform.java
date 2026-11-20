package first.tacz.render.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 只改写 GLSL 标识符和输入声明，注释及预处理内容保持原样。
public final class ShaderTransform {
    public Result transform(String source, int binding, int mode, String kernel) {
        List<Token> tokens = new ArrayList<>();
        for (int at = 0; at < source.length();) {
            int start = at;
            char ch = source.charAt(at++);
            if (ch == '/' && at < source.length() && source.charAt(at) == '/') {
                while (at < source.length() && source.charAt(at) != '\n') at++;
            } else if (ch == '/' && at < source.length() && source.charAt(at) == '*') {
                at++;
                while (at + 1 < source.length() && !(source.charAt(at) == '*' && source.charAt(at + 1) == '/')) at++;
                at = Math.min(source.length(), at + 2);
            } else if (Character.isJavaIdentifierStart(ch)) {
                while (at < source.length() && Character.isJavaIdentifierPart(source.charAt(at))) at++;
                tokens.add(new Token(start, at, source.substring(start, at)));
            }
        }
        Map<String, Input> inputs = new LinkedHashMap<>();
        Map<String, String> aliases = new LinkedHashMap<>();
        for (int i = 0; i + 2 < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.text.equals("in")) {
                Token type = tokens.get(i + 1), name = tokens.get(i + 2);
                String semantic = name.text.startsWith("iris_") ? name.text.substring(5) : name.text;
                if (List.of("Position", "Normal", "Color", "UV0", "UV1", "UV2", "Entity", "at_tangent").contains(semantic)) {
                    inputs.put(name.text, new Input(type.text, name.start, semantic));
                    aliases.put(semantic, name.text);
                }
            }
        }
        if (!aliases.containsKey("Position")) {
            throw new IllegalArgumentException("Shader has no supported position input");
        }
        StringBuilder transformed = new StringBuilder();
        int cursor = 0;
        for (Token token : tokens) {
            transformed.append(source, cursor, token.start);
            Input input = inputs.get(token.text);
            if (input != null && token.start != input.declaration) {
                transformed.append("tr_").append(input.semantic);
            } else if (token.text.equals("main")) {
                transformed.append("tr_original_main");
            } else {
                transformed.append(token.text);
            }
            cursor = token.end;
        }
        transformed.append(source, cursor, source.length());
        String text = transformed.toString();
        int version = text.indexOf("#version");
        int versionEnd = text.indexOf('\n', version);
        int digits = version + 8;
        while (digits < versionEnd && Character.isWhitespace(text.charAt(digits))) digits++;
        int digitsEnd = digits;
        while (digitsEnd < versionEnd && Character.isDigit(text.charAt(digitsEnd))) digitsEnd++;
        int glslVersion = Math.max(430, Integer.parseInt(text.substring(digits, digitsEnd)));
        text = text.substring(0, version) + "#version " + glslVersion + " core" + text.substring(versionEnd);
        int headerEnd = text.indexOf('\n');
        int scan = headerEnd + 1;
        while (scan < text.length()) {
            int end = text.indexOf('\n', scan);
            if (end < 0) end = text.length();
            String line = text.substring(scan, end).trim();
            if (line.startsWith("#extension") || line.isEmpty() || line.startsWith("//")) {
                headerEnd = end;
                scan = end + 1;
            } else {
                break;
            }
        }
        StringBuilder header = new StringBuilder("\n#define TR_MODE " + mode
                + "\nlayout(std430, binding=" + binding + ") readonly buffer TRPoses { uint tr_data[]; };\nin int tr_Bone;\n");
        for (var entry : inputs.entrySet()) {
            Input input = entry.getValue();
            header.append(input.type).append(" tr_").append(input.semantic).append(";\n");
            header.append("#define TR_HAS_").append(input.semantic).append(" 1\n");
            header.append("#define TR_INPUT_").append(input.semantic).append(' ').append(entry.getKey()).append('\n');
            header.append("#define TR_TYPE_").append(input.semantic).append(' ').append(input.type).append('\n');
        }
        return new Result(text.substring(0, headerEnd + 1) + header + text.substring(headerEnd + 1) + '\n' + kernel, aliases);
    }

    private record Token(int start, int end, String text) {}
    private record Input(String type, int declaration, String semantic) {}
    public record Result(String source, Map<String, String> aliases) {}
}
