package vn.cleantv.updater;

import java.util.ArrayList;
import java.util.List;

/** Splits a .sql asset into statements for SQLiteDatabase.execSQL, which runs one at a time. */
final class SqlScript {
    private SqlScript() { }

    /** "--" comment lines go; a CREATE TRIGGER runs up to its "END;", anything else up to a line ending in ";". */
    static List<String> statements(String script) {
        List<String> out = new ArrayList<>();
        StringBuilder now = new StringBuilder();
        for (String raw : script.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("--")) continue;
            now.append(line).append('\n');
            boolean trigger = now.toString().startsWith("CREATE TRIGGER");
            if (trigger ? line.equals("END;") : line.endsWith(";")) {
                out.add(now.toString().trim());
                now.setLength(0);
            }
        }
        return out;
    }
}
