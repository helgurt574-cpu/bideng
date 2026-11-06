package com.example.filiz;

import android.media.AudioTrack;

import java.util.concurrent.atomic.AtomicInteger;

import kotlin.jvm.functions.Function1;

public final class AudioSamplesCallback implements Function1<float[], Integer> {
    public interface ReleasedProvider {
        boolean get();
    }

    public interface TrackProvider {
        AudioTrack get();
    }

    private final int id;
    private final AtomicInteger generation;
    private final ReleasedProvider releasedProvider;
    private final TrackProvider trackProvider;

    public AudioSamplesCallback(
            int id,
            AtomicInteger generation,
            ReleasedProvider releasedProvider,
            TrackProvider trackProvider
    ) {
        this.id = id;
        this.generation = generation;
        this.releasedProvider = releasedProvider;
        this.trackProvider = trackProvider;
    }

    @Override
    public Integer invoke(float[] samples) {
        if (samples == null || samples.length == 0) return 1;
        if (releasedProvider.get() || generation.get() != id) return 0;

        AudioTrack track = trackProvider.get();
        if (track == null) return 0;

        track.write(samples, 0, samples.length, AudioTrack.WRITE_BLOCKING);
        return 1;
    }
}
