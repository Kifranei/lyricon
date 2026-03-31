package io.github.proify.lyricon.subscriber;

import io.github.proify.lyricon.provider.ProviderInfo;

interface IActivePlayerListener {
    void onActiveProviderChanged(in ProviderInfo providerInfo);
    void onSongChanged(in byte[] song);
    void onPlaybackStateChanged(boolean isPlaying);
    void onPositionChanged(long position);
    void onSeekTo(long position);
    void onSendText(String text);
    void onDisplayTranslationChanged(boolean isDisplayTranslation);
    void onDisplayRomaChanged(boolean displayRoma);
}
