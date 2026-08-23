package com.example.pacemetronome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Persists the compact editable program, not the expanded running session. */
public final class StateStore {
    private static final String PREFERENCES = "pace_metronome_settings";
    private static final String KEY_PROGRAM = "program";
    private static final String KEY_POST_TIMER_SECONDS = "post_timer_seconds";
    private static final String KEY_PATTERN_LENGTH = "pattern_length";
    private static final String KEY_REPEAT_COUNT = "repeat_count";
    private static final int DEFAULT_PATTERN_LENGTH = 4;
    private static final int DEFAULT_REPEAT_COUNT = 100;

    private StateStore() {
    }

    public static final class ProgramConfig {
        private final List<TempoStage> visibleStages;
        private final int patternLength;
        private final int repeatCount;

        private ProgramConfig(List<TempoStage> visibleStages, int patternLength, int repeatCount) {
            this.visibleStages = Collections.unmodifiableList(new ArrayList<>(visibleStages));
            this.patternLength = patternLength;
            this.repeatCount = repeatCount;
        }

        public List<TempoStage> getVisibleStages() {
            return visibleStages;
        }

        public int getPatternLength() {
            return patternLength;
        }

        public int getRepeatCount() {
            return repeatCount;
        }
    }

    public static void save(
            Context context,
            List<TempoStage> visibleStages,
            int patternLength,
            int repeatCount,
            long postTimerSeconds
    ) {
        new TempoSchedule(visibleStages);
        ProgramExpander.expand(visibleStages, patternLength, repeatCount);
        preferences(context).edit()
                .putString(KEY_PROGRAM, ScheduleCodec.encode(visibleStages))
                .putInt(KEY_PATTERN_LENGTH, patternLength)
                .putInt(KEY_REPEAT_COUNT, repeatCount)
                .putLong(KEY_POST_TIMER_SECONDS, postTimerSeconds)
                .apply();
    }

    public static ProgramConfig loadProgram(Context context) {
        SharedPreferences prefs = preferences(context);
        String encoded = prefs.getString(KEY_PROGRAM, null);
        if (encoded != null) {
            try {
                ArrayList<TempoStage> decoded = new ArrayList<>(ScheduleCodec.decode(encoded));
                if (isLegacyExample(decoded) || isPreviousExpandedDefault(decoded)) {
                    ProgramConfig defaults = defaultProgram();
                    saveCompactMigration(prefs, defaults);
                    return defaults;
                }

                int patternLength = prefs.getInt(KEY_PATTERN_LENGTH, 0);
                int repeatCount = prefs.getInt(KEY_REPEAT_COUNT, 1);
                ProgramExpander.expand(decoded, patternLength, repeatCount);
                return new ProgramConfig(decoded, patternLength, repeatCount);
            } catch (JSONException | IllegalArgumentException ignored) {
                // Fall through to a known-good compact default.
            }
        }
        return defaultProgram();
    }

    public static long loadPostTimerSeconds(Context context) {
        return Math.max(0L, preferences(context).getLong(KEY_POST_TIMER_SECONDS, 60L));
    }

    /**
     * Compact default. Only seven rows are shown in the UI.
     * The first four form one cycle:
     * 200 -> 300 -> 200 -> 100 -> (next cycle's 200), repeated 100 times.
     * The final 100/50/25 BPM stages run once as a cooldown.
     */
    public static ProgramConfig defaultProgram() {
        ArrayList<TempoStage> stages = new ArrayList<>(7);
        stages.add(new TempoStage(200, 60, 10));
        stages.add(new TempoStage(300, 7, 7));
        stages.add(new TempoStage(200, 60, 10));
        stages.add(new TempoStage(100, 7, 7));
        stages.add(new TempoStage(100, 10 * 60L, 0));
        stages.add(new TempoStage(50, 10 * 60L, 0));
        stages.add(new TempoStage(25, 10 * 60L, 0));
        return new ProgramConfig(stages, DEFAULT_PATTERN_LENGTH, DEFAULT_REPEAT_COUNT);
    }

    private static void saveCompactMigration(SharedPreferences prefs, ProgramConfig config) {
        prefs.edit()
                .putString(KEY_PROGRAM, ScheduleCodec.encode(config.getVisibleStages()))
                .putInt(KEY_PATTERN_LENGTH, config.getPatternLength())
                .putInt(KEY_REPEAT_COUNT, config.getRepeatCount())
                .apply();
    }

    private static boolean isLegacyExample(List<TempoStage> stages) {
        return stages.size() == 2
                && stages.get(0).equals(new TempoStage(100, 60, 0))
                && stages.get(1).equals(new TempoStage(200, 60, 10));
    }

    /** Detects the previous 203-row default so an in-place APK update removes the lag automatically. */
    private static boolean isPreviousExpandedDefault(List<TempoStage> stages) {
        if (stages.size() != 203) {
            return false;
        }
        for (int repetition = 0; repetition < 100; repetition++) {
            if (!stages.get(repetition * 2).equals(new TempoStage(200, 60, 10))
                    || !stages.get(repetition * 2 + 1).equals(new TempoStage(300, 7, 7))) {
                return false;
            }
        }
        return stages.get(200).equals(new TempoStage(100, 600, 0))
                && stages.get(201).equals(new TempoStage(50, 600, 0))
                && stages.get(202).equals(new TempoStage(25, 600, 0));
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
