/**
 * 播放器状态监听器接口。
 * 用于跨进程通知订阅者关于播放器元数据、播放状态以及配置信息的变更。
 */
package io.github.proify.lyricon.subscriber;

import io.github.proify.lyricon.subscriber.ProviderInfo;
import io.github.proify.lyricon.lyric.model.Song;

interface IActivePlayerListener {
    // 当活动的播放源(Provider)发生变化时回调
    void onActiveProviderChanged(in byte[] providerInfo);

    // 当歌曲元数据发生变化时回调
    void onSongChanged(in byte[] song);

    // 当播放状态(播放/暂停)发生变化时回调
    void onPlaybackStateChanged(boolean isPlaying);

    // 响应跳转进度请求
    void onSeekTo(long position);

    // 接收文本消息通知
    void onReceiveText(String text);

    // 翻译显示开关状态变更回调
    void onDisplayTranslationChanged(boolean isDisplayTranslation);

    // 罗马音显示开关状态变更回调
    void onDisplayRomaChanged(boolean isDisplayRoma);
}