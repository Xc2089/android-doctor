package com.androiddoctor.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LogSanitizer {
    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("(?i)(authorization\\s*[:=]\\s*(?:bearer\\s+)?)[^\\s,;]+"), "$1[REDACTED_TOKEN]"),
            new Rule(Pattern.compile("(?i)((?:api[_-]?key|access[_-]?token|refresh[_-]?token|password)\\s*[:=]\\s*)[^\\s,;]+"), "$1[REDACTED_SECRET]"),
            new Rule(Pattern.compile("(?i)(?:[a-z0-9._%+-]+)@(?:[a-z0-9.-]+\\.[a-z]{2,})"), "[REDACTED_EMAIL]"),
            new Rule(Pattern.compile("(?<![\\d.])(?:(?:10|127)(?:\\.\\d{1,3}){3}|169\\.254(?:\\.\\d{1,3}){2}|172\\.(?:1[6-9]|2\\d|3[01])(?:\\.\\d{1,3}){2}|192\\.168(?:\\.\\d{1,3}){2})(?![\\d.])"), "[REDACTED_PRIVATE_IP]"),
            new Rule(Pattern.compile("(?i)(?<![0-9a-f])(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}(?![0-9a-f])"), "[REDACTED_MAC]"),
            new Rule(Pattern.compile("(?i)((?:imei|imsi|android_id|serial(?:no|number)?)\\s*[:=]\\s*)[a-z0-9-]{6,}"), "$1[REDACTED_DEVICE_ID]"),
            new Rule(Pattern.compile("(?i)(/Users/|/home/|C:\\\\Users\\\\)[^/\\\\\\s]+"), "$1[REDACTED_USER]")
    );

    public SanitizedLog sanitize(String input) {
        String result = input.replace("\u0000", "");
        int redactions = 0;
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern.matcher(result);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                redactions++;
                matcher.appendReplacement(buffer, rule.replacement);
            }
            matcher.appendTail(buffer);
            result = buffer.toString();
        }
        return new SanitizedLog(result, redactions);
    }

    private record Rule(Pattern pattern, String replacement) {}
    public record SanitizedLog(String value, int redactionCount) {}
}

