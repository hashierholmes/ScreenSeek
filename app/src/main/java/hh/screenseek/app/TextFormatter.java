package hh.screenseek.app;

/**
 * Normalizes common Markdown returned by Gemini for the native result view.
 *
 * This is intentionally a small formatter rather than a full Markdown parser;
 * ScreenSeek only needs the subset used by its response presentation.
 */
public class TextFormatter {

    public static String cleanMarkdown(String text) {
        // Keep formatting predictable for the lightweight TextView result UI.
        if (text == null || text.isEmpty()) return "";

        text = text.replaceAll("\\*\\*\\*(.*?)\\*\\*\\*", "$1");
        text = text.replaceAll("\\*\\*(.*?)\\*\\*", "$1");
        text = text.replaceAll("\\*(.*?)\\*", "$1");

        text = text.replaceAll("(?m)^\\s*\\*\\s+", "• ");
        text = text.replaceAll("(?m)^\\s*-\\s+", "• ");

        text = text.replaceAll("(?m)^#{1,6}\\s*", "");

        text = text.replaceAll("`", "");

        return text.trim();
    }
}